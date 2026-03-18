package com.demo.commons.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All Redis operations for the trade platform.
 * Used as a shared dependency by every microservice that connects to Redis.
 * Each service supplies its own Redis connection (RedisConfig) but uses
 * this class for all key access — key naming is enforced via RedisKeyScheme.
 */
@Service
public class RedisService {

    private final StringRedisTemplate redis;

    private static final String SELL_VALIDATION_LUA =
            "local current = tonumber(redis.call('HGET', KEYS[1], 'netQuantity')) " +
            "if current == nil then return -1 end " +
            "if current < tonumber(ARGV[1]) then return 0 " +
            "else return 1 end";

    public RedisService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // --- Price ---

    public void savePrice(String symbol, Map<String, String> fields) {
        String key = RedisKeyScheme.price(symbol);
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, Duration.ofHours(2));
    }

    public Map<Object, Object> getPrice(String symbol) {
        return redis.opsForHash().entries(RedisKeyScheme.price(symbol));
    }

    // --- Position ---

    public void savePosition(String accountId, String symbol, int netQuantity, String avgCost, String lastUpdated) {
        String key = RedisKeyScheme.position(accountId, symbol);
        redis.opsForHash().put(key, "netQuantity", String.valueOf(netQuantity));
        redis.opsForHash().put(key, "avgCost", avgCost);
        redis.opsForHash().put(key, "lastUpdated", lastUpdated);
    }

    public Map<Object, Object> getPosition(String accountId, String symbol) {
        return redis.opsForHash().entries(RedisKeyScheme.position(accountId, symbol));
    }

    // --- Portfolio cache ---

    public void cachePortfolio(String accountId, String json) {
        redis.opsForValue().set(RedisKeyScheme.portfolio(accountId), json, Duration.ofSeconds(30));
    }

    public String getCachedPortfolio(String accountId) {
        return redis.opsForValue().get(RedisKeyScheme.portfolio(accountId));
    }

    public void invalidatePortfolio(String accountId) {
        redis.delete(RedisKeyScheme.portfolio(accountId));
    }

    // --- Symbol sets ---

    public void addAccountSymbol(String accountId, String symbol) {
        redis.opsForSet().add(RedisKeyScheme.accountSymbols(accountId), symbol);
    }

    public void removeAccountSymbol(String accountId, String symbol) {
        redis.opsForSet().remove(RedisKeyScheme.accountSymbols(accountId), symbol);
    }

    public Set<String> getAccountSymbols(String accountId) {
        return redis.opsForSet().members(RedisKeyScheme.accountSymbols(accountId));
    }

    public void addSymbolHolder(String symbol, String accountId) {
        redis.opsForSet().add(RedisKeyScheme.symbolHolders(symbol), accountId);
    }

    public void removeSymbolHolder(String symbol, String accountId) {
        redis.opsForSet().remove(RedisKeyScheme.symbolHolders(symbol), accountId);
    }

    public Set<String> getSymbolHolders(String symbol) {
        return redis.opsForSet().members(RedisKeyScheme.symbolHolders(symbol));
    }

    // --- Manager / account mappings ---

    public void addManagerAccount(String managerId, String accountId) {
        redis.opsForSet().add(RedisKeyScheme.managerAccounts(managerId), accountId);
    }

    public Set<String> getManagerAccounts(String managerId) {
        return redis.opsForSet().members(RedisKeyScheme.managerAccounts(managerId));
    }

    public void setAccountManager(String accountId, String managerId) {
        redis.opsForValue().set(RedisKeyScheme.accountManager(accountId), managerId);
    }

    public String getAccountManager(String accountId) {
        return redis.opsForValue().get(RedisKeyScheme.accountManager(accountId));
    }

    // --- Seeder flag ---

    public boolean isSeederCompleted() {
        return "true".equals(redis.opsForValue().get(RedisKeyScheme.seederCompleted()));
    }

    public void markSeederCompleted() {
        redis.opsForValue().set(RedisKeyScheme.seederCompleted(), "true");
    }

    // --- Atomic SELL validation via Lua ---

    public long validateSell(String accountId, String symbol, int requestedQty) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SELL_VALIDATION_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(RedisKeyScheme.position(accountId, symbol)),
                String.valueOf(requestedQty));
        return result != null ? result : -1L;
    }
}
