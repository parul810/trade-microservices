package com.demo.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_audit")
public class TradeAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id")
    private String tradeId;

    @Column(name = "account_id")
    private String accountId;

    private String symbol;
    private String side;
    private int quantity;
    private BigDecimal price;

    @Column(name = "kafka_topic")
    private String kafkaTopic;

    @Column(name = "kafka_partition")
    private int kafkaPartition;

    @Column(name = "kafka_offset")
    private long kafkaOffset;

    @Column(name = "kafka_timestamp")
    private LocalDateTime kafkaTimestamp;

    @Column(name = "consumer_group")
    private String consumerGroup;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    public Long getId()                                        { return id; }

    public String getTradeId()                                 { return tradeId; }
    public void setTradeId(String tradeId)                     { this.tradeId = tradeId; }

    public String getAccountId()                               { return accountId; }
    public void setAccountId(String accountId)                 { this.accountId = accountId; }

    public String getSymbol()                                  { return symbol; }
    public void setSymbol(String symbol)                       { this.symbol = symbol; }

    public String getSide()                                    { return side; }
    public void setSide(String side)                           { this.side = side; }

    public int getQuantity()                                   { return quantity; }
    public void setQuantity(int quantity)                      { this.quantity = quantity; }

    public BigDecimal getPrice()                               { return price; }
    public void setPrice(BigDecimal price)                     { this.price = price; }

    public String getKafkaTopic()                              { return kafkaTopic; }
    public void setKafkaTopic(String kafkaTopic)               { this.kafkaTopic = kafkaTopic; }

    public int getKafkaPartition()                             { return kafkaPartition; }
    public void setKafkaPartition(int kafkaPartition)          { this.kafkaPartition = kafkaPartition; }

    public long getKafkaOffset()                               { return kafkaOffset; }
    public void setKafkaOffset(long kafkaOffset)               { this.kafkaOffset = kafkaOffset; }

    public LocalDateTime getKafkaTimestamp()                   { return kafkaTimestamp; }
    public void setKafkaTimestamp(LocalDateTime kafkaTimestamp){ this.kafkaTimestamp = kafkaTimestamp; }

    public String getConsumerGroup()                           { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup)         { this.consumerGroup = consumerGroup; }

    public LocalDateTime getReceivedAt()                       { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt)        { this.receivedAt = receivedAt; }
}
