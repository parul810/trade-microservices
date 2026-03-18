package com.demo.audit.consumer;

import com.demo.audit.entity.TradeAuditEntity;
import com.demo.audit.repository.TradeAuditRepository;
import com.demo.commons.model.kafka.TradeEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Independent consumer group on trades.raw.topic.
 * Records every trade event with full Kafka metadata for audit and analytics.
 * Has no dependency on execution-service and cannot affect trade processing.
 */
@Service
public class TradeAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeAuditConsumer.class);
    private static final String CONSUMER_GROUP = "audit-group";

    private final TradeAuditRepository tradeAuditRepository;

    public TradeAuditConsumer(TradeAuditRepository tradeAuditRepository) {
        this.tradeAuditRepository = tradeAuditRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.trades-raw}", groupId = "audit-group")
    public void consume(ConsumerRecord<String, TradeEvent> record) {
        TradeEvent event = record.value();

        TradeAuditEntity audit = new TradeAuditEntity();
        audit.setTradeId(event.getTradeId());
        audit.setAccountId(event.getAccountId());
        audit.setSymbol(event.getSymbol());
        audit.setSide(event.getSide());
        audit.setQuantity(event.getQuantity());
        audit.setPrice(event.getPrice());

        audit.setKafkaTopic(record.topic());
        audit.setKafkaPartition(record.partition());
        audit.setKafkaOffset(record.offset());
        audit.setKafkaTimestamp(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault()));
        audit.setConsumerGroup(CONSUMER_GROUP);
        audit.setReceivedAt(LocalDateTime.now());

        tradeAuditRepository.save(audit);

        log.info("Audit recorded | tradeId={} partition={} offset={} account={} symbol={} side={}",
                event.getTradeId(), record.partition(), record.offset(),
                event.getAccountId(), event.getSymbol(), event.getSide());
    }
}
