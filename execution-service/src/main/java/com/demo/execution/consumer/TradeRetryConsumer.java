package com.demo.execution.consumer;

import com.demo.commons.model.kafka.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Consumes from trades.retry.topic and re-publishes to trades.raw.topic after a
 * short delay so the lock contention that caused the retry has time to clear.
 *
 * Why a separate consumer group (trading-retry-group)?
 * Using the same group as TradeConsumer would mean the retry partition competes
 * with live partitions for the same threads. A dedicated group keeps retry
 * processing isolated and prevents a retry storm from slowing live trade processing.
 *
 * Why Thread.sleep for the delay?
 * This consumer has one responsibility: wait, then re-queue. The sleep is intentional
 * and bounded (2s). It does not block any live-trade consumer threads.
 */
@Service
public class TradeRetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeRetryConsumer.class);
    private static final long RETRY_DELAY_MS = 2_000;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.trades-raw}")
    private String tradesRawTopic;

    public TradeRetryConsumer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "${app.kafka.topics.trades-retry}", groupId = "trading-retry-group")
    public void consume(TradeEvent event) {
        log.info("Retrying trade after delay | tradeId={} symbol={}", event.getTradeId(), event.getSymbol());
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        kafkaTemplate.send(tradesRawTopic, event.getAccountId(), event);
        log.info("Trade re-queued to raw topic | tradeId={}", event.getTradeId());
    }
}
