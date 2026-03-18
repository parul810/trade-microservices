package com.demo.portfolio.model;

import java.math.BigDecimal;
import java.util.List;

public class PortfolioResponse {
    private String accountId;
    private String managerId;
    private String managerName;
    private String computedAt;
    private BigDecimal totalValue;
    private BigDecimal totalCost;
    private BigDecimal totalPnl;
    private BigDecimal pnlPct;
    private BigDecimal dayChange;
    private BigDecimal dayChangePct;
    private List<PositionValuation> positions;
    private List<String> unpricedSymbols;
    private String dataSource;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getComputedAt() { return computedAt; }
    public void setComputedAt(String computedAt) { this.computedAt = computedAt; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public BigDecimal getTotalPnl() { return totalPnl; }
    public void setTotalPnl(BigDecimal totalPnl) { this.totalPnl = totalPnl; }

    public BigDecimal getPnlPct() { return pnlPct; }
    public void setPnlPct(BigDecimal pnlPct) { this.pnlPct = pnlPct; }

    public BigDecimal getDayChange() { return dayChange; }
    public void setDayChange(BigDecimal dayChange) { this.dayChange = dayChange; }

    public BigDecimal getDayChangePct() { return dayChangePct; }
    public void setDayChangePct(BigDecimal dayChangePct) { this.dayChangePct = dayChangePct; }

    public List<PositionValuation> getPositions() { return positions; }
    public void setPositions(List<PositionValuation> positions) { this.positions = positions; }

    public List<String> getUnpricedSymbols() { return unpricedSymbols; }
    public void setUnpricedSymbols(List<String> unpricedSymbols) { this.unpricedSymbols = unpricedSymbols; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
