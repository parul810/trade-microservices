package com.demo.portfolio.consumer;

import com.demo.commons.model.kafka.TradeProcessedEvent;
import com.demo.commons.redis.RedisService;
import com.demo.portfolio.service.PortfolioPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TradeProcessedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeProcessedConsumer.class);

    private final RedisService redisService;
    private final PortfolioPushService pushService;

    public TradeProcessedConsumer(RedisService redisService, PortfolioPushService pushService) {
        this.redisService = redisService;
        this.pushService  = pushService;
    }

    @KafkaListener(topics = "${app.kafka.topics.trades-processed}", groupId = "portfolio-cache-group")
    public void consume(TradeProcessedEvent event) {
        log.info("TradeProcessed received | tradeId={} accountId={} status={}",
                event.getTradeId(), event.getAccountId(), event.getStatus());

        redisService.invalidatePortfolio(event.getAccountId());
        pushService.pushPortfolio(event.getAccountId());

        log.info("Portfolio pushed via WS after trade | accountId={}", event.getAccountId());
    }
}
