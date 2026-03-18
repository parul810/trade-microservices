package com.demo.price.controller;

import com.demo.commons.model.kafka.PriceEvent;
import com.demo.commons.redis.RedisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/prices")
public class PriceController {

    private final RedisService redisService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.supported-symbols}")
    private List<String> supportedSymbols;

    @Value("${app.kafka.topics.prices-live}")
    private String pricesLiveTopic;

    public PriceController(RedisService redisService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisService  = redisService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Map<Object, Object>>> getAllPrices() {
        Map<String, Map<Object, Object>> prices = supportedSymbols.stream()
                .collect(Collectors.toMap(symbol -> symbol, redisService::getPrice));
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<Object, Object>> getPrice(@PathVariable("symbol") String symbol) {
        Map<Object, Object> price = redisService.getPrice(symbol);
        if (price.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(price);
    }

    // Manual price update — publishes to prices.live.topic, flows through PriceConsumer → Redis
    @PostMapping
    public ResponseEntity<Map<String, String>> updatePrice(@RequestBody PriceUpdateRequest request) {
        // Use current Redis price as prevClose so day-change % is meaningful
        Map<Object, Object> existing = redisService.getPrice(request.getSymbol());
        BigDecimal prevClose = existing.isEmpty() ? request.getPrice()
                : new BigDecimal((String) existing.getOrDefault("price", request.getPrice().toPlainString()));

        PriceEvent event = new PriceEvent(
                request.getSymbol(),
                request.getPrice(),
                request.getPrice(),
                request.getPrice(),
                request.getPrice(),
                prevClose,
                Instant.now().toString(),
                "manual"
        );
        kafkaTemplate.send(pricesLiveTopic, request.getSymbol(), event);
        return ResponseEntity.ok(Map.of(
                "status", "published",
                "symbol", request.getSymbol(),
                "price",  request.getPrice().toString()
        ));
    }

    public static class PriceUpdateRequest {
        private String symbol;
        private BigDecimal price;

        public String getSymbol()          { return symbol; }
        public void setSymbol(String v)    { this.symbol = v; }

        public BigDecimal getPrice()       { return price; }
        public void setPrice(BigDecimal v) { this.price = v; }
    }
}
