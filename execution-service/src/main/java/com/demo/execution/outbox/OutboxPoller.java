package com.demo.execution.outbox;

import com.demo.commons.model.kafka.TradeProcessedEvent;
import com.demo.execution.entity.TradeOutboxEntry;
import com.demo.execution.repository.TradeOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls trade_outbox every 5 seconds for unpublished entries and publishes
 * them to trades.processed.topic.
 *
 * Why this approach instead of direct kafkaTemplate.send() in TradeConsumer?
 * The outbox row is written inside the same DB transaction as the trade/position update.
 * Even if the JVM crashes after the DB commit, this poller will pick up the row and
 * publish the event on the next tick — the event is never silently lost.
 *
 * Idempotency: portfolio-service cache invalidation is idempotent (delete-by-key),
 * so publishing the same TradeProcessedEvent twice is safe.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final TradeOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.trades-processed}")
    private String tradesProcessedTopic;

    public OutboxPoller(TradeOutboxRepository outboxRepository,
                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate    = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<TradeOutboxEntry> pending =
                outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.debug("Outbox poll: {} unpublished entries", pending.size());

        for (TradeOutboxEntry entry : pending) {
            try {
                TradeProcessedEvent event =
                        new TradeProcessedEvent(entry.getTradeId(), entry.getAccountId(), entry.getStatus());
                kafkaTemplate.send(tradesProcessedTopic, entry.getAccountId(), event).get(); // sync send

                entry.setPublished(true);
                entry.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(entry);

                log.info("Outbox published | tradeId={} status={}", entry.getTradeId(), entry.getStatus());
            } catch (Exception e) {
                // Leave published=false — next poll will retry this entry.
                log.error("Outbox publish failed | tradeId={} reason={}", entry.getTradeId(), e.getMessage());
                break; // stop processing further entries if broker is down
            }
        }
    }
}
