package com.demo.execution.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Outbox table entry for TradeProcessedEvent.
 *
 * Written inside the same @Transactional as the trade/position DB update,
 * so the event record is guaranteed to exist if and only if the trade committed.
 * A separate OutboxPoller reads unpublished rows and publishes to Kafka,
 * eliminating the gap where a trade is ACCEPTED in DB but the Kafka publish fails.
 */
@Entity
@Table(name = "trade_outbox")
public class TradeOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false)
    private String tradeId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public Long getId() { return id; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
