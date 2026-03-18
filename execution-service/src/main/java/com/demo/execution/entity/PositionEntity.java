package com.demo.execution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "positions")
@IdClass(PositionEntity.PositionId.class)
public class PositionEntity {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Id
    private String symbol;

    @Column(name = "net_quantity")
    private int netQuantity;

    @Column(name = "avg_cost")
    private BigDecimal avgCost;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getNetQuantity() { return netQuantity; }
    public void setNetQuantity(int netQuantity) { this.netQuantity = netQuantity; }

    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public static class PositionId implements Serializable {
        private String accountId;
        private String symbol;

        public PositionId() {}

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PositionId that = (PositionId) o;
            return Objects.equals(accountId, that.accountId) && Objects.equals(symbol, that.symbol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, symbol);
        }
    }
}
