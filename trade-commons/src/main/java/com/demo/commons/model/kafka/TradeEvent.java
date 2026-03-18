package com.demo.commons.model.kafka;

import java.math.BigDecimal;

/**
 * Published by trade-service to trades.raw.topic.
 * Consumed by execution-service (trading-group) and audit-service (audit-group).
 */
public class TradeEvent {
    private String tradeId;
    private String accountId;
    private String managerId;
    private String symbol;
    private String side;
    private int quantity;
    private BigDecimal price;
    private String timestamp;

    public TradeEvent() {}

    public TradeEvent(String tradeId, String accountId, String managerId, String symbol, String side,
                      int quantity, BigDecimal price, String timestamp) {
        this.tradeId   = tradeId;
        this.accountId = accountId;
        this.managerId = managerId;
        this.symbol    = symbol;
        this.side      = side;
        this.quantity  = quantity;
        this.price     = price;
        this.timestamp = timestamp;
    }

    public String getTradeId()                   { return tradeId; }
    public void setTradeId(String tradeId)       { this.tradeId = tradeId; }

    public String getAccountId()                 { return accountId; }
    public void setAccountId(String accountId)   { this.accountId = accountId; }

    public String getManagerId()                 { return managerId; }
    public void setManagerId(String managerId)   { this.managerId = managerId; }

    public String getSymbol()                    { return symbol; }
    public void setSymbol(String symbol)         { this.symbol = symbol; }

    public String getSide()                      { return side; }
    public void setSide(String side)             { this.side = side; }

    public int getQuantity()                     { return quantity; }
    public void setQuantity(int quantity)        { this.quantity = quantity; }

    public BigDecimal getPrice()                 { return price; }
    public void setPrice(BigDecimal price)       { this.price = price; }

    public String getTimestamp()                 { return timestamp; }
    public void setTimestamp(String timestamp)   { this.timestamp = timestamp; }
}
