package com.demo.portfolio.consumer;

import com.demo.commons.model.kafka.PriceUpdatedEvent;
import com.demo.commons.redis.RedisService;
import com.demo.portfolio.service.PortfolioPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PriceUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdatedConsumer.class);

    private final RedisService redisService;
    private final PortfolioPushService pushService;

    public PriceUpdatedConsumer(RedisService redisService, PortfolioPushService pushService) {
        this.redisService = redisService;
        this.pushService  = pushService;
    }

    @KafkaListener(topics = "${app.kafka.topics.prices-updated}", groupId = "portfolio-cache-group")
    public void consume(PriceUpdatedEvent event) {
        log.debug("PriceUpdated received | symbol={} price={}", event.getSymbol(), event.getPrice());

        Set<String> accountIds = redisService.getSymbolHolders(event.getSymbol());
        if (accountIds == null || accountIds.isEmpty()) return;

        for (String accountId : accountIds) {
            redisService.invalidatePortfolio(accountId);
            pushService.pushPortfolio(accountId);
        }

        log.debug("Portfolio pushed via WS for {} accounts holding {}", accountIds.size(), event.getSymbol());
    }
}
