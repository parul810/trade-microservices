package com.demo.price.producer;

import com.demo.commons.model.kafka.PriceEvent;
import com.demo.commons.redis.RedisService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fetches real baseline prices from Yahoo Finance v8 on startup (sequential, 1s apart).
 * On each scheduled tick, applies a small random walk (±0.5%) to simulate live movement.
 * Falls back to hardcoded reference prices if Yahoo is unavailable.
 */
@Service
public class PriceProducer {

    private static final Logger log = LoggerFactory.getLogger(PriceProducer.class);

    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=5d";

    // Reference prices (approximate) used as fallback when Yahoo is unavailable
    private static final Map<String, Double> REFERENCE_PRICES = Map.ofEntries(
            Map.entry("AAPL",  254.0),
            Map.entry("MSFT",  398.0),
            Map.entry("GOOGL", 168.0),
            Map.entry("NVDA",  116.0),
            Map.entry("META",  594.0),
            Map.entry("AMZN",  196.0),
            Map.entry("JPM",   245.0),
            Map.entry("GS",    566.0),
            Map.entry("BAC",    43.0),
            Map.entry("MS",    126.0),
            Map.entry("BNPQY",  31.0),
            Map.entry("TSLA",  237.0),
            Map.entry("NFLX",  952.0),
            Map.entry("DIS",    97.0),
            Map.entry("XOM",   111.0),
            Map.entry("JNJ",   158.0)
    );

    @Value("${app.kafka.topics.prices-live}")
    private String topic;

    @Value("${app.supported-symbols}")
    private List<String> supportedSymbols;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final RedisService redisService;
    private final Random random = new Random();

    // Live prices updated each tick via random walk
    private final Map<String, BigDecimal> currentPrices = new HashMap<>();
    private final Map<String, BigDecimal> prevCloses    = new HashMap<>();

    public PriceProducer(KafkaTemplate<String, Object> kafkaTemplate,
                         RestTemplate restTemplate,
                         RedisService redisService) {
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate  = restTemplate;
        this.redisService  = redisService;
    }

    @PostConstruct
    public void initPrices() {
        log.info("Initialising baseline prices (Yahoo Finance v8, sequential)...");
        for (String symbol : supportedSymbols) {
            BigDecimal price = fetchRealPrice(symbol);
            currentPrices.put(symbol, price);
            prevCloses.put(symbol, price);
            log.info("Baseline price | symbol={} price={}", symbol, price);
            try { Thread.sleep(1_200); } catch (InterruptedException ignored) {}
        }
        log.info("Baseline prices initialised for {} symbols", currentPrices.size());
    }

    public void fetchAndPublishAll() {
        for (String symbol : supportedSymbols) {
            BigDecimal base   = currentPrices.getOrDefault(symbol,
                    BigDecimal.valueOf(REFERENCE_PRICES.getOrDefault(symbol, 100.0)));
            BigDecimal next   = applyRandomWalk(base);
            BigDecimal prev   = prevCloses.getOrDefault(symbol, base);

            // Intraday high/low widen around current price
            BigDecimal high   = next.max(base).multiply(BigDecimal.valueOf(1 + random.nextDouble() * 0.003))
                                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal low    = next.min(base).multiply(BigDecimal.valueOf(1 - random.nextDouble() * 0.003))
                                    .setScale(2, RoundingMode.HALF_UP);

            currentPrices.put(symbol, next);

            PriceEvent event = new PriceEvent(
                    symbol, next, base, high, low, prev,
                    Instant.now().toString(), "simulated"
            );

            kafkaTemplate.send(topic, symbol, event);
            log.debug("Price published | symbol={} price={}", symbol, next);
        }
        log.info("Price tick published for {} symbols", supportedSymbols.size());
    }

    /** Applies ±1.5% random walk to the given price. */
    private BigDecimal applyRandomWalk(BigDecimal price) {
        double changePct = (random.nextDouble() * 3.0) - 1.5; // -1.5% to +1.5%
        double factor = 1.0 + changePct / 100.0;
        return price.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fetches real market price from Yahoo Finance v8. Returns reference price on any failure. */
    private BigDecimal fetchRealPrice(String symbol) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            var response = restTemplate.exchange(
                    YAHOO_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class, symbol);

            if (response.getBody() == null) return referencePrice(symbol);

            @SuppressWarnings("unchecked")
            Map<String, Object> chart = (Map<String, Object>) response.getBody().get("chart");
            if (chart == null) return referencePrice(symbol);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return referencePrice(symbol);

            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) results.get(0).get("meta");
            if (meta == null) return referencePrice(symbol);

            Object price = meta.get("regularMarketPrice");
            if (price instanceof Number n) {
                return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("Yahoo fetch failed for symbol={}, using reference price. reason={}", symbol, e.getMessage());
        }
        return referencePrice(symbol);
    }

    private BigDecimal referencePrice(String symbol) {
        return BigDecimal.valueOf(REFERENCE_PRICES.getOrDefault(symbol, 100.0))
                         .setScale(2, RoundingMode.HALF_UP);
    }
}
