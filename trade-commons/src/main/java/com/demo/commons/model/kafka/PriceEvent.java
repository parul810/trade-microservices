package com.demo.commons.model.kafka;

import java.math.BigDecimal;

/**
 * Published by price-service to prices.live.topic.
 * Consumed by price-service (price-consumer-group) to write Redis.
 */
public class PriceEvent {
    private String symbol;
    private BigDecimal price;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal prevClose;
    private String timestamp;
    private String source;

    public PriceEvent() {}

    public PriceEvent(String symbol, BigDecimal price, BigDecimal open, BigDecimal high,
                      BigDecimal low, BigDecimal prevClose, String timestamp, String source) {
        this.symbol    = symbol;
        this.price     = price;
        this.open      = open;
        this.high      = high;
        this.low       = low;
        this.prevClose = prevClose;
        this.timestamp = timestamp;
        this.source    = source;
    }

    public String getSymbol()                      { return symbol; }
    public void setSymbol(String symbol)           { this.symbol = symbol; }

    public BigDecimal getPrice()                   { return price; }
    public void setPrice(BigDecimal price)         { this.price = price; }

    public BigDecimal getOpen()                    { return open; }
    public void setOpen(BigDecimal open)           { this.open = open; }

    public BigDecimal getHigh()                    { return high; }
    public void setHigh(BigDecimal high)           { this.high = high; }

    public BigDecimal getLow()                     { return low; }
    public void setLow(BigDecimal low)             { this.low = low; }

    public BigDecimal getPrevClose()               { return prevClose; }
    public void setPrevClose(BigDecimal prevClose) { this.prevClose = prevClose; }

    public String getTimestamp()                   { return timestamp; }
    public void setTimestamp(String timestamp)     { this.timestamp = timestamp; }

    public String getSource()                      { return source; }
    public void setSource(String source)           { this.source = source; }
}
