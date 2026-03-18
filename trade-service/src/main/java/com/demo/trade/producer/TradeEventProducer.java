package com.demo.trade.producer;

import com.demo.commons.model.kafka.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TradeEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);

    @Value("${app.kafka.topics.trades-raw}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TradeEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TradeEvent event) {
        // Key = accountId — same account always routes to same partition
        kafkaTemplate.send(topic, event.getAccountId(), event);
        log.info("Trade published | tradeId={} account={} symbol={} side={} qty={}",
                event.getTradeId(), event.getAccountId(),
                event.getSymbol(), event.getSide(), event.getQuantity());
    }
}
