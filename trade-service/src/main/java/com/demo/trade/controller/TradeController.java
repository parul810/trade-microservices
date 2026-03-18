package com.demo.trade.controller;

import com.demo.commons.model.kafka.TradeEvent;
import com.demo.commons.redis.RedisService;
import com.demo.trade.producer.TradeEventProducer;
import com.demo.trade.service.TradeValidationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/trades")
public class TradeController {

    private final TradeValidationService validationService;
    private final TradeEventProducer tradeEventProducer;
    private final RedisService redisService;

    public TradeController(TradeValidationService validationService,
                           TradeEventProducer tradeEventProducer,
                           RedisService redisService) {
        this.validationService  = validationService;
        this.tradeEventProducer = tradeEventProducer;
        this.redisService       = redisService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submitTrade(@Valid @RequestBody TradeRequest request) {
        try {
            validationService.validateSymbol(request.getSymbol());
            validationService.validateQuantity(request.getQuantity());

            // Read price directly from Redis — price-service writes price:* keys,
            // trade-service reads them via the shared RedisService from trade-commons
            Map<Object, Object> priceData = redisService.getPrice(request.getSymbol());
            BigDecimal price = priceData.isEmpty() ? BigDecimal.ZERO
                    : new BigDecimal((String) priceData.get("price"));

            TradeEvent event = new TradeEvent(
                    UUID.randomUUID().toString(),
                    request.getAccountId(),
                    request.getManagerId() != null ? request.getManagerId() : "PM-001",
                    request.getSymbol(),
                    request.getSide().toUpperCase(),
                    request.getQuantity(),
                    price,
                    Instant.now().toString()
            );

            tradeEventProducer.publish(event);

            return ResponseEntity.accepted().body(Map.of(
                    "tradeId", event.getTradeId(),
                    "status",  "ACCEPTED",
                    "message", "Trade submitted for processing"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "tradeId", "null",
                    "status",  "REJECTED",
                    "message", e.getMessage()
            ));
        }
    }

    public static class TradeRequest {
        @NotBlank(message = "accountId is required")
        private String accountId;

        private String managerId;  // optional — defaults to PM-001

        @NotBlank(message = "symbol is required")
        private String symbol;

        @NotBlank(message = "side is required (BUY or SELL)")
        private String side;

        @Positive(message = "quantity must be greater than 0")
        private int quantity;

        public String getAccountId()       { return accountId; }
        public void setAccountId(String v) { this.accountId = v; }

        public String getManagerId()       { return managerId; }
        public void setManagerId(String v) { this.managerId = v; }

        public String getSymbol()          { return symbol; }
        public void setSymbol(String v)    { this.symbol = v; }

        public String getSide()            { return side; }
        public void setSide(String v)      { this.side = v; }

        public int getQuantity()           { return quantity; }
        public void setQuantity(int v)     { this.quantity = v; }
    }
}
