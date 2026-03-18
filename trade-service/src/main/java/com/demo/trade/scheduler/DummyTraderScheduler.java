package com.demo.trade.scheduler;

import com.demo.commons.model.kafka.TradeEvent;
import com.demo.commons.redis.RedisService;
import com.demo.trade.producer.TradeEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Component
public class DummyTraderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DummyTraderScheduler.class);

    private static final List<String> SYMBOLS = List.of(
            "AAPL", "MSFT", "GOOGL", "NVDA", "META", "AMZN",
            "JPM", "GS", "BAC", "MS", "TSLA", "NFLX"
    );

    // accountId -> managerId
    private static final Map<String, String> ACCOUNTS = Map.of(
            "ACC-001", "PM-001",
            "ACC-002", "PM-002",
            "ACC-003", "PM-003"
    );

    private static final List<String> ACCOUNT_IDS = List.of("ACC-001", "ACC-002", "ACC-003");

    private final TradeEventProducer tradeEventProducer;
    private final RedisService redisService;
    private final Random random = new Random();

    public DummyTraderScheduler(TradeEventProducer tradeEventProducer, RedisService redisService) {
        this.tradeEventProducer = tradeEventProducer;
        this.redisService       = redisService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void placeTrades() {
        log.info("DummyTrader: placing scheduled trades");

        for (String accountId : ACCOUNT_IDS) {
            String managerId = ACCOUNTS.get(accountId);
            String symbol    = SYMBOLS.get(random.nextInt(SYMBOLS.size()));
            String side      = random.nextInt(10) < 7 ? "BUY" : "SELL"; // 70% BUY
            int    quantity  = 10 + random.nextInt(41); // 10-50

            BigDecimal price = getPriceForSymbol(symbol);

            TradeEvent event = new TradeEvent(
                    UUID.randomUUID().toString(),
                    accountId,
                    managerId,
                    symbol,
                    side,
                    quantity,
                    price,
                    Instant.now().toString()
            );

            tradeEventProducer.publish(event);
            log.info("DummyTrader: {} {} {} qty={} price={} account={} manager={}",
                    side, quantity, symbol, quantity, price, accountId, managerId);
        }
    }

    private BigDecimal getPriceForSymbol(String symbol) {
        Map<Object, Object> priceData = redisService.getPrice(symbol);
        if (priceData != null && priceData.containsKey("price")) {
            try {
                return new BigDecimal(priceData.get("price").toString());
            } catch (Exception ignored) {}
        }
        // fallback: zero price if not yet populated
        return BigDecimal.ZERO;
    }
}
