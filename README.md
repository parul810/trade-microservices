# Trade Microservices

A real-time trading platform built as an event-driven microservices system using Spring Boot, Apache Kafka, Redis, and PostgreSQL. The platform handles trade submission, execution, position management, live price feeds, and portfolio valuation with sub-second WebSocket push to a browser dashboard.

## Architecture

![Architecture](architecture.drawio)

```
Browser Dashboard
      │  REST (JWT)          WebSocket (STOMP)
      ▼                            ▲
auth-service   trade-service   portfolio-service
:8084          :8085           :8087
                   │                 │ ▲
              trades.raw        prices.updated
                   │            trades.processed
                   ▼                 │
          execution-service   price-service
          :8086               :8083
               │                    │
          audit-service        prices.live
          :8082                     │
               │             PriceConsumer  PriceRedisConsumer
          PostgreSQL               │              │
                              prices.updated    Redis
```

### Services

| Service | Port | Responsibility |
|---|---|---|
| **auth-service** | 8084 | Issues JWT tokens via `POST /auth/login` |
| **trade-service** | 8085 | Accepts trades, publishes to Kafka; runs DummyTrader scheduler |
| **execution-service** | 8086 | Validates and executes trades; owns position state in PostgreSQL |
| **portfolio-service** | 8087 | Computes portfolio valuations; pushes real-time updates via WebSocket |
| **price-service** | 8083 | Fetches baseline prices from Yahoo Finance; simulates live market (±1.5% / 15s) |
| **audit-service** | 8082 | Independent audit trail consumer; records full Kafka metadata |

### Kafka Topics

| Topic | Producer | Consumers | Key |
|---|---|---|---|
| `trades.raw` | trade-service | execution-service, audit-service | accountId |
| `trades.processed` | execution-service (OutboxPoller) | portfolio-service | accountId |
| `prices.live` | price-service | PriceConsumer, PriceRedisConsumer | symbol |
| `prices.updated` | price-service (PriceConsumer) | portfolio-service | symbol |

### Redis Key Schema

| Key | Type | Owner | TTL |
|---|---|---|---|
| `price:{symbol}` | Hash (OHLCV) | price-service | 2h |
| `pos:{accountId}:{symbol}` | Hash (qty, avgCost) | execution-service | none |
| `account:symbols:{accountId}` | Set | execution-service | none |
| `symbol:holders:{symbol}` | Set (reverse index) | execution-service | none |
| `portfolio:{accountId}` | String (JSON) | portfolio-service | 30s |
| `account:manager:{accountId}` | String | execution-service | none |
| `manager:accounts:{managerId}` | Set | execution-service | none |

## Key Design Patterns

**Outbox Pattern** — execution-service writes a `TradeOutboxEntry` row inside the same database transaction as the trade. A separate `OutboxPoller` scheduler publishes to Kafka and marks the entry published only after broker acknowledgment. Guarantees no silent event loss between PostgreSQL commit and Kafka publish.

**Dual Consumer Groups** — `prices.live` is consumed by two independent consumer groups: `price-redis-group` (writes Redis) and `price-consumer-group` (republishes to `prices.updated`). A Redis failure cannot block the Kafka republish; failures are isolated to their own offset.

**Pessimistic Locking** — SELL trades use `SELECT FOR UPDATE` with a 5-second timeout. On lock contention, the trade is routed to `trades.retry` and re-queued after a 2-second delay, preventing thread starvation and negative positions.

**Circuit Breaker** — Resilience4j wraps all Redis writes in execution-service. If Redis is unavailable, the circuit opens, the consumer offset advances, and PostgreSQL remains consistent. Portfolio-service falls back to HTTP against execution-service's `/internal/positions` endpoint.

**Event-Driven Cache Invalidation** — The 30-second TTL on `portfolio:{accountId}` is a fallback only. `TradeProcessedConsumer` and `PriceUpdatedConsumer` in portfolio-service invalidate and push immediately on every relevant event.

**Stateless JWT Auth** — auth-service issues tokens; all other services validate locally via a shared `JwtFilter` from trade-commons. No session state, no auth-service call per request.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose

## Getting Started

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts:
- Redis on `localhost:6380`
- PostgreSQL on `localhost:5433` (db: `trading`, user/pass: `postgres`)
- Kafka broker on `localhost:9094`
- Kafka UI on `localhost:8090`
- Prometheus on `localhost:9090`
- Grafana on `localhost:3000` (admin / admin)

### 2. Build All Services

```bash
mvn clean package -DskipTests
```

### 3. Start Services

Start each service in a separate terminal (order matters for first run):

```bash
# 1 — Auth (no dependencies)
cd auth-service && mvn spring-boot:run

# 2 — Price (initialises Redis price cache at startup)
cd price-service && mvn spring-boot:run

# 3 — Execution (creates DB schema, seeds test data)
cd execution-service && mvn spring-boot:run

# 4 — Trade
cd trade-service && mvn spring-boot:run

# 5 — Portfolio (WebSocket + dashboard)
cd portfolio-service && mvn spring-boot:run

# 6 — Audit (independent, start any time)
cd audit-service && mvn spring-boot:run
```

### 4. Open the Dashboard

Navigate to `http://localhost:8087` in your browser.

Default credentials (seeded by execution-service on first run):

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | Admin |
| `pm1` | `pm1pass` | Portfolio Manager 1 (ACC-001) |
| `pm2` | `pm2pass` | Portfolio Manager 2 (ACC-002) |
| `pm3` | `pm3pass` | Portfolio Manager 3 (ACC-003) |

## API Reference

### Auth

```bash
# Login — returns JWT token
curl -X POST http://localhost:8084/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Prices

```bash
# Get all live prices
curl http://localhost:8083/prices

# Get single symbol
curl http://localhost:8083/prices/AAPL

# Manual price override (publishes to Kafka, flows through to Redis + WebSocket)
curl -X POST http://localhost:8083/prices \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","price":195.50}'
```

### Trades

```bash
TOKEN=<jwt from login>

# Submit a trade
curl -X POST http://localhost:8085/trades \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC-001","symbol":"AAPL","side":"BUY","quantity":10}'
```

### Portfolio

```bash
# Get all managers and their portfolios
curl -H "Authorization: Bearer $TOKEN" http://localhost:8087/managers

# Get portfolio for a specific account
curl -H "Authorization: Bearer $TOKEN" http://localhost:8087/portfolio/ACC-001
```

### Audit

```bash
# Get audit trail for a trade
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/audit/trades/{tradeId}

# Get aggregate stats
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/audit/stats
```

## Monitoring

| Tool | URL | Notes |
|---|---|---|
| Kafka UI | `http://localhost:8090` | Browse topics, consumer group offsets |
| Prometheus | `http://localhost:9090` | Metrics from `/actuator/prometheus` |
| Grafana | `http://localhost:3000` | Dashboards (admin/admin) |

## Project Structure

```
trade-microservices/
├── trade-commons/          # Shared library: Kafka models, Redis service/key scheme, JWT filter
├── auth-service/           # JWT issuance
├── trade-service/          # Trade ingestion + DummyTraderScheduler
├── execution-service/      # Trade execution, position management, Outbox pattern
├── portfolio-service/      # Portfolio valuation, WebSocket push, browser dashboard
├── price-service/          # Yahoo Finance baseline + random walk simulation
├── audit-service/          # Independent audit consumer
├── docker-compose.yml      # Infrastructure: Redis, PostgreSQL, Kafka, Prometheus, Grafana
├── monitoring/             # Prometheus scrape config + Grafana provisioning
├── architecture.drawio     # Full system architecture diagram
└── price-event-flow.drawio # Detailed price event consumer chain diagram
```

## Supported Symbols

AAPL, MSFT, GOOGL, AMZN, NVDA, META, TSLA, JPM, BAC, GS, MS, BRK.B, SPY, QQQ, GLD, TLT

## Technology Stack

| Component | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2 |
| Messaging | Apache Kafka (KRaft mode) |
| Cache | Redis 7 |
| Database | PostgreSQL 16 |
| Security | JWT (JJWT / HS256) |
| Resilience | Resilience4j (circuit breaker) |
| Real-time | Spring WebSocket + STOMP |
| Metrics | Micrometer + Prometheus + Grafana |
| Build | Maven (multi-module) |
