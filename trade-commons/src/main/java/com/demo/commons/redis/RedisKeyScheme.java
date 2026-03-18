package com.demo.commons.redis;

/**
 * Single source of truth for all Redis key names across every microservice.
 * Every service that reads or writes Redis must use these methods —
 * never hardcode key strings in service code.
 *
 * Key patterns:
 *   price:{symbol}                  latest price data (OHLCV), TTL 2h
 *   pos:{accountId}:{symbol}        position for an account+symbol
 *   account:symbols:{accountId}     set of symbols held by an account
 *   symbol:holders:{symbol}         set of accounts holding a symbol
 *   portfolio:{accountId}           cached portfolio valuation, TTL 30s
 *   account:manager:{accountId}     managerId assigned to an account
 *   manager:accounts:{managerId}    set of accounts managed by a manager
 *   seeder:completed                flag set after DataSeeder runs
 */
public class RedisKeyScheme {

    private RedisKeyScheme() {}

    public static String price(String symbol) {
        return "price:" + symbol;
    }

    public static String position(String accountId, String symbol) {
        return "pos:" + accountId + ":" + symbol;
    }

    public static String accountSymbols(String accountId) {
        return "account:symbols:" + accountId;
    }

    public static String symbolHolders(String symbol) {
        return "symbol:holders:" + symbol;
    }

    public static String portfolio(String accountId) {
        return "portfolio:" + accountId;
    }

    public static String accountManager(String accountId) {
        return "account:manager:" + accountId;
    }

    public static String managerAccounts(String managerId) {
        return "manager:accounts:" + managerId;
    }

    public static String seederCompleted() {
        return "seeder:completed";
    }
}
