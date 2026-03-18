package com.demo.commons.model.dto;

import java.math.BigDecimal;

/**
 * Shared DTO for position data exchanged over HTTP between execution-service and portfolio-service.
 * execution-service serialises PositionEntity into this.
 * portfolio-service deserialises the HTTP response into this.
 */
public class PositionDto {
    private String symbol;
    private int netQuantity;
    private BigDecimal avgCost;

    public PositionDto() {}

    public PositionDto(String symbol, int netQuantity, BigDecimal avgCost) {
        this.symbol      = symbol;
        this.netQuantity = netQuantity;
        this.avgCost     = avgCost;
    }

    public String getSymbol()                  { return symbol; }
    public void setSymbol(String symbol)       { this.symbol = symbol; }

    public int getNetQuantity()                { return netQuantity; }
    public void setNetQuantity(int netQuantity){ this.netQuantity = netQuantity; }

    public BigDecimal getAvgCost()             { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
}
