package com.demo.commons.model.kafka;

/**
 * Published by execution-service to trades.processed.topic after a trade is processed.
 * Consumed by portfolio-service (portfolio-cache-group) to invalidate portfolio:* cache.
 *
 * This decouples execution-service from portfolio cache ownership —
 * execution-service says "a trade was processed", portfolio-service
 * decides what to do with that information.
 */
public class TradeProcessedEvent {
    private String tradeId;
    private String accountId;
    private String status;    // ACCEPTED or REJECTED

    public TradeProcessedEvent() {}

    public TradeProcessedEvent(String tradeId, String accountId, String status) {
        this.tradeId   = tradeId;
        this.accountId = accountId;
        this.status    = status;
    }

    public String getTradeId()                 { return tradeId; }
    public void setTradeId(String tradeId)     { this.tradeId = tradeId; }

    public String getAccountId()               { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getStatus()                  { return status; }
    public void setStatus(String status)       { this.status = status; }
}
