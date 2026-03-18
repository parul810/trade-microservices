# From Monolith to Event-Driven Microservices: Designing a Real-Time Trade Platform

*The architectural decisions behind a production-grade trading system — and the hard lessons that shaped them.*

---

## The Starting Point: A Monolith That Made Sense

Every system starts as a monolith. Not because engineers don't know better, but because a monolith is exactly the right choice when you're figuring out your domain.

In the beginning, a trading platform is straightforward. A user submits a trade. The trade gets validated. A position is updated. An audit record is written. Done. A single Spring Boot application with a PostgreSQL database handles all of this perfectly well. You can deploy it, debug it, test it, and reason about it.

The trouble starts when the domain grows teeth.

Add a price feed that updates 16 symbols every 15 seconds. Add a portfolio valuation engine that needs to reflect those price changes in real time. Add WebSocket push to a browser dashboard showing live PnL. Add an audit service with its own retention policy. Add a compliance requirement that says the audit trail must be independent of trade processing. Add a scheduler that needs to simulate trading activity. Add monitoring. Add CORS for a browser client.

Suddenly your monolith is doing ten things, and the failure modes of each thing are tangled together. A slow database query in the audit module delays trade execution. A Redis connection issue in the price module brings down the whole application. A deployment to fix a UI bug requires redeploying the entire trading engine.

That's the moment you stop thinking about code and start thinking about architecture.

---

## The Decision to Decompose

Decomposing a monolith is not a technical decision. It's a domain decision. The question is never "should I have microservices?" The question is "where are my actual boundaries?"

After 19 years of building systems — financial platforms, logistics networks, real-time data pipelines — I've learned that the right boundaries come from answering one question for each candidate service: *can this component fail independently without taking anything else down with it?*

For a trading platform, the answer gives you six services:

- **auth-service** — Issues JWT tokens. It has no awareness of trades, prices, or portfolios. If it goes down, existing sessions continue working because tokens are stateless. New logins fail, but no trade data is corrupted.
- **trade-service** — Accepts trade requests and publishes them to Kafka. Its only job is to validate the input and hand off to the message bus. It has no database, no position state, no price logic.
- **execution-service** — Validates and executes trades against real position data. It owns PostgreSQL. It owns position integrity. Nothing else does.
- **price-service** — Fetches baseline prices from an external feed at startup, then simulates a live market via random walk. It publishes to Kafka. It does not know what trades exist.
- **portfolio-service** — Computes valuations on demand. It has no database of its own. It reads positions and prices from Redis. It pushes results to browsers via WebSocket.
- **audit-service** — Records every trade event independently. It shares the same Kafka topic as execution-service but runs in a completely separate consumer group. An audit failure cannot block a trade.

This decomposition is deliberate. Each service has a single reason to change. If the price feed changes from Yahoo Finance to Bloomberg, only price-service is touched. If the portfolio calculation formula changes, only portfolio-service is touched. If compliance needs a new field in the audit trail, only audit-service is touched.

---

## The Shared Kernel: trade-commons

One of the classic mistakes in microservices is duplicating code across services. Each team writes their own Kafka serializer, their own Redis client, their own JWT validator. Six months later you have six different interpretations of what a "trade" is, and a data inconsistency that takes three weeks to root-cause.

The trade-commons library solves this with a shared kernel that every service depends on. But it does so carefully.

**RedisKeyScheme** is the single source of truth for every Redis key name in the platform:

```
price:{symbol}               → OHLCV hash, TTL 2h
pos:{accountId}:{symbol}     → position hash (qty, avg cost)
account:symbols:{accountId}  → set of symbols an account holds
symbol:holders:{symbol}      → reverse index: accounts holding this symbol
portfolio:{accountId}        → cached valuation JSON, TTL 30s
account:manager:{accountId}  → managerId string
manager:accounts:{managerId} → set of accounts under this manager
seeder:completed             → startup flag
```

This is not over-engineering. This is enforcing a contract. If execution-service writes `pos:ACC-001:AAPL` and portfolio-service tries to read `position:ACC001:AAPL`, you have a runtime bug with no compile-time warning. A shared key scheme makes that class of bug impossible.

The **Kafka event models** — `TradeEvent`, `TradeProcessedEvent`, `PriceEvent`, `PriceUpdatedEvent` — live here too. When the portfolio-service consumes a `PriceUpdatedEvent`, it is using the exact same class definition that price-service used to produce it. Schema evolution is managed in one place.

The **JWT filter** is shared, but with a nuance. auth-service is the only service that *issues* tokens. Every other service *validates* them using the shared `JwtFilter`. But auth-service doesn't need a Redis client. So trade-commons uses selective imports rather than pulling in every dependency for every service. auth-service explicitly imports only what it needs:

```java
@SpringBootApplication
@Import({JwtService.class, JwtFilter.class})
public class AuthServiceApplication { ... }
```

Other services use package scanning. The distinction is intentional: prevent classpath bloat and the initialization errors that come with it.

---

## Kafka as the Spine

In a distributed system, you need something that services can talk *to* rather than *at each other*. Direct HTTP calls between services create tight coupling, cascading failures, and temporal dependency — the caller has to be up when the callee is processing.

Kafka solves this by making communication asynchronous and durable. The full topic topology is:

```
trades.raw          ← trade-service publishes; execution + audit consume
trades.retry        ← execution-service re-queues on lock contention
trades.processed    ← OutboxPoller publishes; portfolio-service consumes
prices.live         ← price-service publishes; PriceConsumer + PriceRedisConsumer consume
prices.updated      ← PriceConsumer republishes; portfolio-service consumes
```

Two design choices here are worth explaining.

**Partitioning by natural key.** `trades.raw` is partitioned by `accountId`. This means all trades for account ACC-001 land on the same partition, in order. Execution-service processes them sequentially for that account. This eliminates the need for distributed locking across position updates — if trades are ordered, the position update is also ordered. `prices.live` is partitioned by symbol, for the same reason.

**Separate topics for different abstraction levels.** `prices.live` carries the full `PriceEvent` (price, open, high, low, prevClose, source). `prices.updated` carries only a lightweight `PriceUpdatedEvent` (symbol, price). The separation is intentional: different consumers need different payloads. PriceRedisConsumer needs the full OHLCV. portfolio-service's PriceUpdatedConsumer only needs to know *which symbol changed* so it can find affected accounts and push updates. You shouldn't pay the serialization cost of a full PriceEvent for a consumer that only needs two fields.

---

## The Dual-Consumer Pattern: Decoupling Within a Service

One of the subtler patterns in this architecture is how price-service consumes from its own topic.

When PriceProducer publishes a `PriceEvent` to `prices.live`, two things need to happen:
1. The full OHLCV data must be written to Redis
2. A lightweight `PriceUpdatedEvent` must be published to `prices.updated` so portfolio-service can react

The naive implementation puts both operations in a single consumer:

```java
// What you might write first
@KafkaListener(topics = "prices.live", groupId = "price-group")
public void consume(PriceEvent event) {
    redisService.savePrice(event.getSymbol(), ...);          // writes Redis
    kafkaTemplate.send("prices.updated", event.getSymbol(), ...);  // publishes Kafka
}
```

This looks fine until Redis has a connection issue. The Kafka publish fails *because* the Redis write failed, even though the two operations have nothing to do with each other. The portfolio push notification is blocked by a Redis problem.

The production-grade solution is two independent consumer groups:

```java
// PriceRedisConsumer — owns Redis writes
@KafkaListener(topics = "prices.live", groupId = "price-redis-group")
public void consume(PriceEvent event) {
    redisService.savePrice(event.getSymbol(), Map.of(...));
}

// PriceConsumer — owns Kafka republish
@KafkaListener(topics = "prices.live", groupId = "price-consumer-group")
public void consume(PriceEvent event) {
    kafkaTemplate.send("prices.updated", event.getSymbol(),
        new PriceUpdatedEvent(event.getSymbol(), event.getPrice()));
}
```

Each consumer group maintains its own offset. If the Redis write fails, Kafka does not commit that offset — the message is retried independently. The Kafka republish continues unaffected. You've replaced a dual-write problem with two single-write responsibilities.

This is the same principle as having separate threads for I/O and computation. Failures are isolated to their domain.

---

## The Outbox Pattern: Guaranteed Event Delivery

The most dangerous moment in any event-driven system is after you've committed to the database but before you've published to Kafka.

Consider the naive approach in execution-service:

```java
@KafkaListener(topics = "trades.raw")
@Transactional
public void consume(TradeEvent event) {
    tradeRepository.save(trade);          // DB commit
    kafkaTemplate.send("trades.processed", event);  // Kafka publish
    // What if the JVM crashes here?
}
```

If the JVM crashes after `save()` but before `send()`, the trade is in the database as ACCEPTED but portfolio-service never receives the event to invalidate its cache. The user sees stale data indefinitely.

The Outbox Pattern solves this by making the Kafka publish itself a database write:

```java
@Transactional
public void processTradeWithOutbox(TradeEntity trade) {
    trade.setStatus("ACCEPTED");
    tradeRepository.save(trade);

    // Written inside the SAME transaction
    TradeOutboxEntry outbox = new TradeOutboxEntry();
    outbox.setTradeId(trade.getTradeId());
    outbox.setStatus("ACCEPTED");
    outbox.setPublished(false);
    outboxRepository.save(outbox);

    // Commit: both trade + outbox entry are atomically written
}

// Separate scheduler, not part of the transaction
@Scheduled(fixedDelay = 5000)
public void publishPending() {
    List<TradeOutboxEntry> pending =
        outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
    for (TradeOutboxEntry entry : pending) {
        kafkaTemplate.send(topic, entry.getAccountId(), buildEvent(entry)).get(); // sync
        entry.setPublished(true);
        outboxRepository.save(entry);
    }
}
```

The trade commit and the outbox entry commit happen atomically. The `OutboxPoller` runs on a 5-second schedule, picks up any unpublished entries, and publishes them synchronously (`.get()` on the `CompletableFuture`). Only after broker acknowledgment is the `published` flag set.

If the JVM crashes between commit and publish, `OutboxPoller` picks up the unpublished entry on the next startup. The event is delivered exactly once to portfolio-service. No silent data loss.

This is not an academic pattern. In production financial systems, silent event loss between the database and the message bus is one of the most common and hardest-to-detect classes of bugs.

---

## Handling Race Conditions: Pessimistic Locking for SELL

Position management under concurrent load has a classic race condition:

```
Time 0: Account ACC-001 holds 100 shares of AAPL
Time 1: Thread A receives SELL 60 AAPL → reads qty=100 ✓
Time 2: Thread B receives SELL 60 AAPL → reads qty=100 ✓
Time 3: Thread A writes qty=40
Time 4: Thread B writes qty=40
Result: qty=40 (should be -20, caught by validation — or silently wrong)
```

The fix is a database-level exclusive lock on the position row during SELL processing:

```java
PositionEntity pos = entityManager.find(
    PositionEntity.class,
    positionId,
    LockModeType.PESSIMISTIC_WRITE,
    Map.of("javax.persistence.lock.timeout", 5000L)
);
```

`PESSIMISTIC_WRITE` maps to `SELECT ... FOR UPDATE` in PostgreSQL. Thread B blocks until Thread A commits. When the lock releases, Thread B reads `qty=40`, attempts to SELL 60, finds insufficient quantity, and the trade is REJECTED — correctly.

The 5-second timeout is important. Without it, Thread B waits indefinitely if Thread A crashes while holding the lock, starving the consumer thread. With the timeout, after 5 seconds a `PessimisticLockingFailureException` is thrown. The consumer catches it, deletes the PENDING trade record, and routes the event to `trades.retry`:

```java
} catch (PessimisticLockingFailureException ex) {
    // Undo the PENDING write before re-queuing
    tradeRepository.deleteById(trade.getTradeId());
    kafkaTemplate.send("trades.retry", event.getAccountId(), event);
}
```

The retry consumer waits 2 seconds before re-publishing to `trades.raw`. This gives the lock holder time to finish, reducing contention on the next attempt. And because `trades.retry` is a separate consumer group from `trades.raw`, the retry traffic doesn't block the main processing pipeline.

---

## Circuit Breakers: Preventing Cascade Failures

The single most common source of cascading failures in microservices is synchronous dependencies. Service A calls service B. Service B is slow. Service A's thread pool fills up with blocked threads. Service A becomes unresponsive. Service C, which depends on A, also becomes unresponsive. The whole system goes down because of a slow database query in service B.

In this architecture, the main risk is Redis. Every service reads and writes Redis constantly. If Redis has a connection issue, naive code throws an exception and the Kafka offset doesn't advance — the message is retried repeatedly, the consumer thread blocks, and the trade pipeline stalls.

The solution is Resilience4j wrapping every Redis write in execution-service:

```java
redisCircuitBreaker.executeRunnable(() -> {
    redisService.savePosition(accountId, symbol, qty, avgCost, timestamp);
    redisService.addAccountSymbol(accountId, symbol);
    redisService.addSymbolHolder(symbol, accountId);
    redisService.setAccountManager(accountId, managerId);
});
```

When Redis is unavailable, the circuit opens. Subsequent calls fail-fast without attempting a Redis connection. The trade consumer continues processing — the trade is written to PostgreSQL, the outbox entry is created, the Kafka offset advances. The Redis cache is stale, but the system is operational.

Portfolio-service handles the Redis-unavailable scenario with a graceful fallback:

```
Cache miss → try Redis for positions → if unavailable,
    HTTP GET /internal/positions/{accountId} from execution-service
```

This fallback trades performance for availability. Under normal conditions, all position reads come from Redis at microsecond latency. If Redis is down, portfolio-service degrades gracefully to a slightly slower HTTP call rather than failing entirely.

The key insight: **Redis is a performance optimization, not a source of truth.** The source of truth is PostgreSQL in execution-service. This distinction, enforced architecturally from day one, is what makes graceful degradation possible.

---

## Portfolio Valuation: Zero Database Dependency

Portfolio-service is the most interesting service in the architecture because it owns no data.

It has no PostgreSQL connection. It does not have a `positions` table. It computes the portfolio valuation entirely from data other services have already written to Redis:

```
position qty        ← written by execution-service (pos:{accountId}:{symbol})
avg cost            ← written by execution-service
current price       ← written by price-service (price:{symbol})
prevClose           ← written by price-service (at startup)
manager mapping     ← written by execution-service (account:manager:{accountId})
```

The computation itself is straightforward:

```
for each position:
    marketValue    = qty × currentPrice
    unrealisedPnl  = marketValue - (avgCost × qty)
    dayChange      = (currentPrice - prevClose) × qty
    direction      = UP | DOWN | FLAT

portfolio total:
    totalValue     = Σ marketValues
    totalPnl       = totalValue - totalCost
    dayChangePct   = Σ dayChanges / (totalValue - Σ dayChanges)
```

The result is cached in Redis with a 30-second TTL (`portfolio:{accountId}`). But TTL is not the primary invalidation mechanism — it's the fallback. Active invalidation is event-driven:

- When execution-service accepts a trade, it publishes to `trades.processed`. `TradeProcessedConsumer` in portfolio-service receives this, invalidates the cache for that account, and pushes a fresh valuation via WebSocket.
- When price-service publishes a new price, `PriceUpdatedConsumer` in portfolio-service looks up `symbol:holders:{symbol}` to find all accounts holding that symbol, invalidates their caches, and pushes fresh valuations.

The reverse index — `symbol:holders:{symbol}` — is the key to efficient invalidation. Without it, every price update would require scanning all portfolios. With it, a price change for AAPL instantly identifies which of the thousands of accounts need to be updated. Execution-service maintains this index as a side effect of trade processing.

This is the data model doing the heavy lifting so the query doesn't have to.

---

## Audit as a First-Class Citizen

In financial systems, the audit trail is not an afterthought. It is a regulatory requirement, and it must be guaranteed even when everything else is failing.

`TradeAuditConsumer` achieves this by being completely independent:

```java
@KafkaListener(topics = "trades.raw", groupId = "audit-group")
public void consume(ConsumerRecord<String, TradeEvent> record) {
    TradeAuditEntity audit = new TradeAuditEntity();
    // Business fields
    audit.setTradeId(event.getTradeId());
    audit.setAccountId(event.getAccountId());
    audit.setSymbol(event.getSymbol());
    audit.setSide(event.getSide());
    // Kafka metadata for dispute resolution
    audit.setKafkaTopic(record.topic());
    audit.setKafkaPartition(record.partition());
    audit.setKafkaOffset(record.offset());
    audit.setKafkaTimestamp(record.timestamp());
    audit.setReceivedAt(LocalDateTime.now());

    tradeAuditRepository.save(audit);
}
```

It runs in its own consumer group (`audit-group`). When execution-service's consumer is at offset 1000 and audit-service is at offset 998, they are completely independent. A slow audit write does not delay trade execution. An audit failure does not cause trade reprocessing.

The Kafka metadata is deliberate. If a counterparty disputes a trade, you can prove not just that the event was received, but *when* it was received by the broker, which partition it landed on, and what offset it had. This is the difference between "we received the trade" and "we can prove we received the trade at 14:32:07.443 UTC."

---

## Real-Time Push: From Event to Browser

The WebSocket integration completes the loop. A price changes in price-service. Within sub-second latency, the browser dashboard reflects the new portfolio valuation.

The path:

```
PriceProducer (@Scheduled, 15s)
  → prices.live.topic
    → PriceConsumer (price-service)
      → prices.updated.topic
        → PriceUpdatedConsumer (portfolio-service)
          → getSymbolHolders(symbol)
          → for each accountId: invalidatePortfolio + pushPortfolio
            → PortfolioPushService
              → messagingTemplate.convertAndSend(
                  "/topic/portfolio/" + accountId,
                  response)
                → Browser STOMP subscriber
```

The browser subscribes to `/topic/portfolio/{accountId}` on WebSocket connect. When `PriceUpdatedConsumer` triggers a push, every browser tab showing that account's portfolio receives the update simultaneously. No polling. No stale data. Sub-second market price reflection.

The key design principle: the push is not the primary computation — it's the trigger. The portfolio valuation logic lives in `PortfolioService.computePortfolio()`. The WebSocket infrastructure is a delivery mechanism. This separation means the REST endpoint (`GET /portfolio/{accountId}`) and the WebSocket push use identical computation code. There is no "real-time version" of the valuation that differs from the "REST version."

---

## Stateless Security at Scale

JWT-based stateless authentication enables horizontal scaling without coordination.

auth-service issues a signed JWT containing the user's identity and roles. Every other service validates this token using the shared `JwtFilter` from trade-commons, performing a local cryptographic verification — no network call to auth-service required.

The implications:
- auth-service can go down after a user logs in; their session continues for the token lifetime
- Any instance of any service can handle any request; there is no affinity requirement
- The security surface is exactly the signing key, not a session store

The filter chain in every service:

```java
SessionCreationPolicy.STATELESS         // No HTTP sessions
CSRF disabled                           // Not applicable for stateless APIs
/actuator/** → permitAll               // Prometheus scraping
All other routes → authenticated        // JWT required
JwtFilter runs before default auth filter
```

auth-service itself imports only `JwtService` and `JwtFilter` from trade-commons. It does not scan for `RedisService` or any Kafka infrastructure. This selective import prevents Spring Boot from trying to auto-configure Redis and Kafka beans that auth-service has no use for — a failure mode that manifests as cryptic startup exceptions in production.

---

## The Monolith-to-Microservices Journey: What You Actually Gain

Having built this platform in both forms — monolith first, then decomposed — the practical differences are worth articulating honestly.

**What you gain:**

*Independent deployability.* Changing the portfolio calculation formula requires deploying only portfolio-service. Trade execution is unaffected. The window of risk for each deployment is narrower.

*Independent scalability.* If price updates are the bottleneck, you scale price-service and its consumers. You don't scale the entire application.

*Failure isolation.* When the audit service has a database issue at 2 AM, the on-call engineer doesn't wake up to "trading is down." They wake up to "audit writes are backed up." Different runbooks, different urgency, different blast radius.

*Technology independence.* If you decide to replace PostgreSQL with a time-series database for audit data, only audit-service changes. Other services don't notice.

**What you pay for it:**

*Distributed transactions don't exist.* A position update and a portfolio cache invalidation are eventually consistent, not strongly consistent. You manage this with the Outbox Pattern and TTL-based fallbacks, but you can never pretend it isn't there.

*Operational complexity.* Six services mean six deployment pipelines, six monitoring dashboards, six sets of logs to correlate. The Kafka offset difference between `audit-group` at 998 and `trading-group` at 1000 is a metric you must monitor.

*The shared library is a double-edged sword.* trade-commons enforces consistency, but a breaking change to a Kafka event model requires coordinated deployment of every service that consumes it.

The trade-off is worth it for a trading platform at scale. It would not be worth it for an internal CRUD tool with ten users.

---

## Design Principles in Retrospect

After building this system, the principles that proved their value:

**1. Name your data contracts.** `RedisKeyScheme` is 53 lines of code that eliminates an entire class of runtime bugs. Every shared resource — every Redis key, every Kafka topic name — lives in the shared library. Not in comments. Not in documentation. In code.

**2. Let the data model do the work.** The `symbol:holders:{symbol}` reverse index in Redis is the difference between O(accounts) and O(1) for price-driven cache invalidation. The query complexity is paid once at write time, not repeatedly at read time.

**3. Failure modes are features, not edge cases.** The circuit breaker for Redis, the pessimistic lock timeout for SELL, the retry topic for lock contention — these are first-class parts of the design. They were not added after observing failures. They were designed in because failures are certain.

**4. One responsibility, one ownership.** execution-service owns positions. price-service owns prices. portfolio-service computes valuations but owns neither. When there is a discrepancy between what portfolio-service shows and what execution-service has, you know exactly where to look.

**5. The source of truth is PostgreSQL.** Redis is a performance optimization. This distinction, never blurred, is what makes the circuit breaker meaningful. If Redis is also the source of truth, a Redis failure means data loss. If Redis is a cache, a Redis failure means slower reads.

**6. Separate consumer groups for separate concerns.** `PriceConsumer` and `PriceRedisConsumer` share a topic but never share a failure domain. `trading-group` and `audit-group` consume the same events for different purposes and fail independently. This is how you compose behavior from simple pieces.

---

## Conclusion

The architecture described here didn't emerge from a design meeting. It evolved from a monolith that started simple and grew complex, and from each complexity forcing a decision: absorb it or isolate it.

The Outbox Pattern was added the first time we traced a silent data loss to the gap between a database commit and a Kafka publish. Pessimistic locking was added the first time a SELL trade produced a negative position. The dual-consumer pattern was added the first time a Redis timeout blocked a portfolio push notification.

Good architecture is not the absence of problems. It is a set of boundaries within which problems stay small.

The full source code is available at [github.com/parul810/trade-microservices](https://github.com/parul810/trade-microservices).

---

*Written from 19 years of building distributed systems — financial platforms, real-time data pipelines, and the occasional lesson in what happens when you don't use pessimistic locking.*
