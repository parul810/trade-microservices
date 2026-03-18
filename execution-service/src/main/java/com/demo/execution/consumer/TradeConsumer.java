package com.demo.execution.consumer;

import com.demo.commons.model.kafka.TradeEvent;
import com.demo.commons.redis.RedisService;
import com.demo.execution.entity.PositionEntity;
import com.demo.execution.entity.TradeEntity;
import com.demo.execution.entity.TradeOutboxEntry;
import com.demo.execution.repository.TradeOutboxRepository;
import com.demo.execution.repository.TradeRepository;
import com.demo.execution.service.PositionService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Listens to trades.raw.topic and:
 * 1. Persists trade to PostgreSQL (PK violation catches duplicates)
 * 2. Updates position in PostgreSQL (PESSIMISTIC_WRITE for SELL)
 * 3. Updates position cache in Redis — protected by a circuit breaker.
 *    If Redis is down, cache writes are skipped so the Kafka consumer thread
 *    stays healthy and offsets keep advancing. PostgreSQL remains the source of truth.
 * 4. Updates symbol tracking sets in Redis — same circuit breaker protection.
 * 5. Publishes TradeProcessedEvent → trades.processed.topic
 *
 * KEY DIFFERENCE from monolith: portfolio cache invalidation is decoupled.
 * execution-service does NOT call redisService.invalidatePortfolio() directly —
 * it fires a Kafka event and lets portfolio-service own its own cache.
 */
@Service
public class TradeConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeConsumer.class);

    private final TradeRepository tradeRepository;
    private final TradeOutboxRepository outboxRepository;
    private final PositionService positionService;
    private final RedisService redisService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreaker redisCircuitBreaker;

    @Value("${app.kafka.topics.trades-retry}")
    private String tradesRetryTopic;

    public TradeConsumer(TradeRepository tradeRepository,
                         TradeOutboxRepository outboxRepository,
                         PositionService positionService,
                         RedisService redisService,
                         KafkaTemplate<String, Object> kafkaTemplate,
                         CircuitBreakerRegistry circuitBreakerRegistry) {
        this.tradeRepository     = tradeRepository;
        this.outboxRepository    = outboxRepository;
        this.positionService     = positionService;
        this.redisService        = redisService;
        this.kafkaTemplate       = kafkaTemplate;
        this.redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
    }

    @KafkaListener(topics = "${app.kafka.topics.trades-raw}", groupId = "trading-group")
    @Transactional
    public void consume(TradeEvent event) {
        log.info("Trade received | tradeId={} account={} symbol={} side={} qty={}",
                event.getTradeId(), event.getAccountId(),
                event.getSymbol(), event.getSide(), event.getQuantity());

        // Step 1: Persist trade — PK violation detects Kafka at-least-once redelivery
        TradeEntity trade = new TradeEntity();
        trade.setTradeId(event.getTradeId());
        trade.setAccountId(event.getAccountId());
        trade.setSymbol(event.getSymbol());
        trade.setSide(event.getSide());
        trade.setQuantity(event.getQuantity());
        trade.setPrice(event.getPrice());
        trade.setStatus("PENDING");
        trade.setCreatedAt(LocalDateTime.now());
        try {
            tradeRepository.save(trade);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate trade ignored | tradeId={}", event.getTradeId());
            return;
        }

        // Step 2: Update position in PostgreSQL
        PositionEntity updatedPos;
        try {
            updatedPos = positionService.updatePosition(
                    event.getAccountId(), event.getSymbol(),
                    event.getSide(), event.getQuantity(), event.getPrice());
        } catch (PessimisticLockingFailureException e) {
            // Another SELL is holding the row lock and did not release within 5s.
            // Delete the PENDING trade so it is not treated as a duplicate on retry,
            // then re-queue the original event for a delayed second attempt.
            tradeRepository.delete(trade);
            kafkaTemplate.send(tradesRetryTopic, event.getAccountId(), event);
            log.warn("Trade queued for retry (lock timeout) | tradeId={} symbol={}",
                    event.getTradeId(), event.getSymbol());
            return;
        } catch (IllegalArgumentException e) {
            trade.setStatus("REJECTED");
            trade.setRejectReason(e.getMessage());
            tradeRepository.save(trade);
            log.warn("Trade rejected | tradeId={} reason={}", event.getTradeId(), e.getMessage());
            saveOutboxEntry(event.getTradeId(), event.getAccountId(), "REJECTED");
            return;
        }

        // Step 3: Mark trade ACCEPTED
        trade.setStatus("ACCEPTED");
        tradeRepository.save(trade);

        // Steps 4+5: Update Redis cache — protected by circuit breaker.
        // If Redis is down the circuit opens and these writes are skipped so the
        // Kafka consumer thread stays alive and offsets keep advancing.
        // PostgreSQL is the source of truth; the cache will catch up on recovery.
        updateRedisCache(event.getAccountId(), event.getSymbol(), updatedPos, event.getManagerId());

        // Step 6: Write to outbox — OutboxPoller will publish TradeProcessedEvent to Kafka.
        // Written inside this @Transactional so the outbox row is guaranteed to exist
        // if and only if the trade committed. Eliminates the gap where a trade is ACCEPTED
        // in DB but the Kafka publish silently fails.
        saveOutboxEntry(event.getTradeId(), event.getAccountId(), "ACCEPTED");

        log.info("Trade processed | tradeId={} status=ACCEPTED newQty={}",
                event.getTradeId(), updatedPos.getNetQuantity());
    }

    private void saveOutboxEntry(String tradeId, String accountId, String status) {
        TradeOutboxEntry entry = new TradeOutboxEntry();
        entry.setTradeId(tradeId);
        entry.setAccountId(accountId);
        entry.setStatus(status);
        entry.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(entry);
    }

    private void updateRedisCache(String accountId, String symbol, PositionEntity pos, String managerId) {
        try {
            redisCircuitBreaker.executeRunnable(() -> {
                redisService.savePosition(
                        accountId, symbol,
                        pos.getNetQuantity(),
                        pos.getAvgCost().toPlainString(),
                        pos.getLastUpdated().toString());

                if (pos.getNetQuantity() > 0) {
                    redisService.addAccountSymbol(accountId, symbol);
                    redisService.addSymbolHolder(symbol, accountId);
                } else {
                    redisService.removeAccountSymbol(accountId, symbol);
                    redisService.removeSymbolHolder(symbol, accountId);
                }

                // Register account → manager mapping (idempotent — safe on every trade)
                if (managerId != null) {
                    redisService.setAccountManager(accountId, managerId);
                    redisService.addManagerAccount(managerId, accountId);
                }
            });
        } catch (Exception e) {
            // Circuit is OPEN or Redis call failed — log and continue.
            // The trade is already committed to PostgreSQL; cache will recover.
            log.warn("Redis cache update skipped | account={} symbol={} reason={}",
                    accountId, symbol, e.getMessage());
        }
    }

}
