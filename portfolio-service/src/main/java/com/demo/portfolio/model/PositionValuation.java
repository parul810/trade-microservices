package com.demo.portfolio.model;

import java.math.BigDecimal;

public class PositionValuation {
    private String symbol;
    private int quantity;
    private BigDecimal avgCost;
    private BigDecimal currentPrice;
    private BigDecimal prevClose;
    private BigDecimal marketValue;
    private BigDecimal unrealisedPnl;
    private BigDecimal pnlPct;
    private BigDecimal dayChange;
    private String direction;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public BigDecimal getPrevClose() { return prevClose; }
    public void setPrevClose(BigDecimal prevClose) { this.prevClose = prevClose; }

    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }

    public BigDecimal getUnrealisedPnl() { return unrealisedPnl; }
    public void setUnrealisedPnl(BigDecimal unrealisedPnl) { this.unrealisedPnl = unrealisedPnl; }

    public BigDecimal getPnlPct() { return pnlPct; }
    public void setPnlPct(BigDecimal pnlPct) { this.pnlPct = pnlPct; }

    public BigDecimal getDayChange() { return dayChange; }
    public void setDayChange(BigDecimal dayChange) { this.dayChange = dayChange; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
}
