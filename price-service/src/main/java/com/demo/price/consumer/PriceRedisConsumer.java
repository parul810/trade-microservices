package com.demo.price.consumer;

import com.demo.commons.model.kafka.PriceEvent;
import com.demo.commons.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Listens to prices.live.topic (price-redis-group) and writes full price data to Redis.
 *
 * Runs as a separate consumer group from PriceConsumer (price-consumer-group) so that
 * each consumer has exactly one responsibility:
 *   PriceConsumer      → Kafka publish only (prices.updated.topic)
 *   PriceRedisConsumer → Redis write only   (price:{symbol} hash)
 *
 * If the Redis write fails, the Kafka offset is not committed and the message is retried
 * independently — without affecting the Kafka publish in PriceConsumer.
 */
@Service
public class PriceRedisConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceRedisConsumer.class);

    private final RedisService redisService;

    public PriceRedisConsumer(RedisService redisService) {
        this.redisService = redisService;
    }

    @KafkaListener(topics = "${app.kafka.topics.prices-live}", groupId = "price-redis-group")
    public void consume(PriceEvent event) {
        redisService.savePrice(event.getSymbol(), Map.of(
                "price",     event.getPrice().toPlainString(),
                "open",      event.getOpen().toPlainString(),
                "high",      event.getHigh().toPlainString(),
                "low",       event.getLow().toPlainString(),
                "prevClose", event.getPrevClose().toPlainString(),
                "timestamp", event.getTimestamp(),
                "source",    event.getSource()
        ));

        log.info("Price cached in Redis | symbol={} price={}", event.getSymbol(), event.getPrice());
    }
}
