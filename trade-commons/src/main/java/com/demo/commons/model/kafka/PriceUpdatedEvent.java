package com.demo.commons.model.kafka;

import java.math.BigDecimal;

/**
 * Published by price-service to prices.updated.topic after Redis is updated.
 * Consumed by portfolio-service (portfolio-price-group) to invalidate
 * portfolio:* cache for all accounts holding the affected symbol.
 *
 * Lighter than PriceEvent — carries only what consumers need to act on,
 * not the full OHLCV payload.
 */
public class PriceUpdatedEvent {
    private String symbol;
    private BigDecimal price;

    public PriceUpdatedEvent() {}

    public PriceUpdatedEvent(String symbol, BigDecimal price) {
        this.symbol = symbol;
        this.price  = price;
    }

    public String getSymbol()              { return symbol; }
    public void setSymbol(String symbol)   { this.symbol = symbol; }

    public BigDecimal getPrice()           { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
