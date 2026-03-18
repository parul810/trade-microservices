package com.demo.portfolio.service;

import com.demo.commons.model.dto.PositionDto;
import com.demo.commons.redis.RedisService;
import com.demo.portfolio.model.PortfolioResponse;
import com.demo.portfolio.model.PositionValuation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes portfolio valuation by combining position data and price data from Redis.
 *
 * KEY DIFFERENCE from monolith:
 * - DB fallback uses HTTP call to execution-service /internal/positions/{accountId}
 *   instead of a direct JPA repository call. portfolio-service has no Postgres dependency.
 * - Cache invalidation is driven by Kafka events (TradeProcessedConsumer, PriceUpdatedConsumer)
 *   rather than happening inline within the trade processing flow.
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final RedisService redisService;
    private final ManagerService managerService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.execution-service.base-url}")
    private String executionServiceBaseUrl;

    public PortfolioService(RedisService redisService,
                            ManagerService managerService,
                            RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.redisService         = redisService;
        this.managerService       = managerService;
        this.restTemplate         = restTemplate;
        this.objectMapper         = objectMapper;
    }

    public PortfolioResponse computePortfolio(String accountId) throws Exception {

        // Step 1: Check Redis cache first
        try {
            String cached = redisService.getCachedPortfolio(accountId);
            if (cached != null) {
                log.info("Portfolio cache hit for accountId={}", accountId);
                return objectMapper.readValue(cached, PortfolioResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for accountId={}, recomputing", accountId);
        }

        log.info("Portfolio cache miss — recomputing for accountId={}", accountId);

        boolean positionsFromRedis = true;
        List<PositionValuation> valuations = new ArrayList<>();
        List<String> unpricedSymbols       = new ArrayList<>();

        BigDecimal totalValue     = BigDecimal.ZERO;
        BigDecimal totalCost      = BigDecimal.ZERO;
        BigDecimal totalDayChange = BigDecimal.ZERO;

        // Step 2: Try Redis positions
        Set<String> symbols = null;
        try {
            symbols = redisService.getAccountSymbols(accountId);
        } catch (Exception e) {
            log.warn("Redis unavailable for accountId={}, falling back to execution-service", accountId);
            positionsFromRedis = false;
        }

        if (positionsFromRedis && (symbols == null || symbols.isEmpty())) {
            return emptyPortfolio(accountId, "redis-cache");
        }

        if (positionsFromRedis) {
            // Step 3a: Redis path
            for (String symbol : symbols) {
                Map<Object, Object> posData;
                Map<Object, Object> priceData;
                try {
                    posData   = redisService.getPosition(accountId, symbol);
                    priceData = redisService.getPrice(symbol);
                } catch (Exception e) {
                    log.warn("Redis read failed mid-loop for symbol={}, skipping", symbol);
                    unpricedSymbols.add(symbol);
                    continue;
                }

                if (posData.isEmpty()) continue;

                int qty = Integer.parseInt((String) posData.get("netQuantity"));
                if (qty <= 0) continue;

                BigDecimal avgCost = new BigDecimal((String) posData.get("avgCost"));

                if (priceData.isEmpty()) {
                    unpricedSymbols.add(symbol);
                    continue;
                }

                BigDecimal currentPrice = new BigDecimal((String) priceData.get("price"));
                BigDecimal prevClose    = new BigDecimal((String) priceData.getOrDefault("prevClose", "0"));

                PositionValuation pv = buildValuation(symbol, qty, avgCost, currentPrice, prevClose);
                valuations.add(pv);

                totalValue     = totalValue.add(pv.getMarketValue());
                totalCost      = totalCost.add(avgCost.multiply(BigDecimal.valueOf(qty)));
                totalDayChange = totalDayChange.add(pv.getDayChange());
            }
        } else {
            // Step 3b: HTTP fallback to execution-service
            List<PositionDto> positions = fetchPositionsFromExecutionService(accountId);
            if (positions.isEmpty()) {
                return emptyPortfolio(accountId, "execution-service");
            }

            for (PositionDto pos : positions) {
                if (pos.getNetQuantity() <= 0) continue;

                BigDecimal currentPrice = null;
                BigDecimal prevClose    = BigDecimal.ZERO;
                try {
                    Map<Object, Object> priceData = redisService.getPrice(pos.getSymbol());
                    if (!priceData.isEmpty()) {
                        currentPrice = new BigDecimal((String) priceData.get("price"));
                        prevClose    = new BigDecimal((String) priceData.getOrDefault("prevClose", "0"));
                    }
                } catch (Exception ignored) {}

                if (currentPrice == null) {
                    unpricedSymbols.add(pos.getSymbol());
                    continue;
                }

                PositionValuation pv = buildValuation(
                        pos.getSymbol(), pos.getNetQuantity(), pos.getAvgCost(), currentPrice, prevClose);
                valuations.add(pv);

                totalValue     = totalValue.add(pv.getMarketValue());
                totalCost      = totalCost.add(pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getNetQuantity())));
                totalDayChange = totalDayChange.add(pv.getDayChange());
            }
        }

        // Step 4: Aggregate totals
        BigDecimal totalPnl      = totalValue.subtract(totalCost);
        BigDecimal pnlPct        = totalCost.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : totalPnl.divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        BigDecimal prevTotalValue = totalValue.subtract(totalDayChange);
        BigDecimal dayChangePct   = prevTotalValue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : totalDayChange.divide(prevTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        String managerId   = null;
        String managerName = null;
        try {
            managerId   = redisService.getAccountManager(accountId);
            managerName = managerId != null ? managerService.getManagerName(managerId) : null;
        } catch (Exception e) {
            log.warn("Redis unavailable for manager lookup of accountId={}", accountId);
        }

        String dataSource = positionsFromRedis ? "redis-cache" : "execution-service";

        PortfolioResponse response = new PortfolioResponse();
        response.setAccountId(accountId);
        response.setManagerId(managerId);
        response.setManagerName(managerName);
        response.setComputedAt(Instant.now().toString());
        response.setTotalValue(totalValue.setScale(2, RoundingMode.HALF_UP));
        response.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        response.setTotalPnl(totalPnl.setScale(2, RoundingMode.HALF_UP));
        response.setPnlPct(pnlPct.setScale(2, RoundingMode.HALF_UP));
        response.setDayChange(totalDayChange.setScale(2, RoundingMode.HALF_UP));
        response.setDayChangePct(dayChangePct.setScale(2, RoundingMode.HALF_UP));
        response.setPositions(valuations);
        response.setUnpricedSymbols(unpricedSymbols);
        response.setDataSource(dataSource);

        // Step 5: Cache for 30 seconds
        try {
            redisService.cachePortfolio(accountId, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("Redis cache write failed for accountId={}", accountId);
        }

        return response;
    }

    private List<PositionDto> fetchPositionsFromExecutionService(String accountId) {
        try {
            String url = executionServiceBaseUrl + "/internal/positions/" + accountId;
            return restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<PositionDto>>() {}
            ).getBody();
        } catch (Exception e) {
            log.error("Failed to fetch positions from execution-service for accountId={}: {}", accountId, e.getMessage());
            return List.of();
        }
    }

    private PositionValuation buildValuation(String symbol, int qty, BigDecimal avgCost,
                                             BigDecimal currentPrice, BigDecimal prevClose) {
        BigDecimal marketValue   = currentPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal costBasis     = avgCost.multiply(BigDecimal.valueOf(qty));
        BigDecimal unrealisedPnl = marketValue.subtract(costBasis);
        BigDecimal pnlPct        = costBasis.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : unrealisedPnl.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        BigDecimal dayChange     = currentPrice.subtract(prevClose).multiply(BigDecimal.valueOf(qty));
        String direction         = currentPrice.compareTo(prevClose) > 0 ? "UP"
                : currentPrice.compareTo(prevClose) < 0 ? "DOWN" : "FLAT";

        PositionValuation pv = new PositionValuation();
        pv.setSymbol(symbol);
        pv.setQuantity(qty);
        pv.setAvgCost(avgCost);
        pv.setCurrentPrice(currentPrice);
        pv.setPrevClose(prevClose);
        pv.setMarketValue(marketValue);
        pv.setUnrealisedPnl(unrealisedPnl);
        pv.setPnlPct(pnlPct.setScale(2, RoundingMode.HALF_UP));
        pv.setDayChange(dayChange);
        pv.setDirection(direction);
        return pv;
    }

    private PortfolioResponse emptyPortfolio(String accountId, String dataSource) {
        PortfolioResponse response = new PortfolioResponse();
        response.setAccountId(accountId);
        response.setComputedAt(Instant.now().toString());
        response.setTotalValue(BigDecimal.ZERO);
        response.setTotalCost(BigDecimal.ZERO);
        response.setTotalPnl(BigDecimal.ZERO);
        response.setPnlPct(BigDecimal.ZERO);
        response.setDayChange(BigDecimal.ZERO);
        response.setDayChangePct(BigDecimal.ZERO);
        response.setPositions(List.of());
        response.setUnpricedSymbols(List.of());
        response.setDataSource(dataSource);
        return response;
    }
}
