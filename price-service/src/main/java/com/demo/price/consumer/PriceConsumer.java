package com.demo.price.consumer;

import com.demo.commons.model.kafka.PriceEvent;
import com.demo.commons.model.kafka.PriceUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Listens to prices.live.topic (price-consumer-group) and publishes PriceUpdatedEvent
 * to prices.updated.topic so downstream services (e.g. portfolio-service) can react.
 *
 * Redis write is intentionally NOT done here — that is handled by PriceRedisConsumer
 * (price-redis-group) which is an independent consumer on the same topic.
 * Splitting into two consumers eliminates the dual-write atomicity problem:
 * each consumer has exactly one responsibility, and a failure in one does not
 * affect the other.
 */
@Service
public class PriceConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceConsumer.class);

    @Value("${app.kafka.topics.prices-updated}")
    private String pricesUpdatedTopic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PriceConsumer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "${app.kafka.topics.prices-live}", groupId = "price-consumer-group", concurrency = "2")
    public void consume(PriceEvent event) {
        PriceUpdatedEvent updatedEvent = new PriceUpdatedEvent(event.getSymbol(), event.getPrice());
        kafkaTemplate.send(pricesUpdatedTopic, event.getSymbol(), updatedEvent);

        log.info("PriceUpdatedEvent published | symbol={} price={}",
                event.getSymbol(), event.getPrice());
    }
}
