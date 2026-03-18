package com.demo.portfolio.model;

import java.util.List;

public class PortfolioManager {
    private String managerId;
    private String name;
    private List<String> accountIds;

    public PortfolioManager() {}

    public PortfolioManager(String managerId, String name, List<String> accountIds) {
        this.managerId  = managerId;
        this.name       = name;
        this.accountIds = accountIds;
    }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getAccountIds() { return accountIds; }
    public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }
}
