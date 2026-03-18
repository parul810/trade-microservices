package com.demo.execution.service;

import com.demo.execution.entity.PositionEntity;
import com.demo.execution.repository.PositionRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Handles position UPSERT in PostgreSQL.
 * BUY  → increases net quantity and recalculates weighted average cost
 * SELL → decreases net quantity (avg cost unchanged); SELECT FOR UPDATE prevents race conditions
 */
@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final EntityManager entityManager;

    public PositionService(PositionRepository positionRepository, EntityManager entityManager) {
        this.positionRepository = positionRepository;
        this.entityManager      = entityManager;
    }

    public PositionEntity updatePosition(String accountId, String symbol,
                                         String side, int quantity, BigDecimal price) {
        if ("SELL".equals(side)) {
            return processSell(accountId, symbol, quantity);
        } else {
            return processBuy(accountId, symbol, quantity, price);
        }
    }

    private PositionEntity processBuy(String accountId, String symbol,
                                      int quantity, BigDecimal price) {
        PositionEntity pos = positionRepository
                .findByAccountIdAndSymbol(accountId, symbol)
                .orElseGet(() -> {
                    PositionEntity newPos = new PositionEntity();
                    newPos.setAccountId(accountId);
                    newPos.setSymbol(symbol);
                    newPos.setNetQuantity(0);
                    newPos.setAvgCost(BigDecimal.ZERO);
                    return newPos;
                });

        int newTotalQty = pos.getNetQuantity() + quantity;
        BigDecimal newAvgCost = (pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getNetQuantity()))
                .add(price.multiply(BigDecimal.valueOf(quantity))))
                .divide(BigDecimal.valueOf(newTotalQty), 4, RoundingMode.HALF_UP);

        pos.setNetQuantity(newTotalQty);
        pos.setAvgCost(newAvgCost);
        pos.setLastUpdated(LocalDateTime.now());
        return positionRepository.save(pos);
    }

    private PositionEntity processSell(String accountId, String symbol, int quantity) {
        // PESSIMISTIC_WRITE = SELECT FOR UPDATE — exclusive row lock.
        // Two concurrent SELLs for the same account+symbol cannot both read
        // the same quantity; one waits, then sees the already-reduced quantity.
        PositionEntity.PositionId id = new PositionEntity.PositionId();
        id.setAccountId(accountId);
        id.setSymbol(symbol);

        // Lock timeout: if another SELL holds this row for > 5s, throw LockTimeoutException
        // rather than waiting indefinitely. TradeConsumer catches this and routes to retry topic.
        PositionEntity pos = entityManager.find(
                PositionEntity.class, id, LockModeType.PESSIMISTIC_WRITE,
                Map.of("javax.persistence.lock.timeout", 5000));

        if (pos == null) {
            throw new IllegalArgumentException(
                    "No position held for symbol: " + symbol + " on account: " + accountId);
        }
        if (pos.getNetQuantity() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient position for SELL. Held: " + pos.getNetQuantity() +
                    ", Requested: " + quantity + ", Symbol: " + symbol);
        }

        pos.setNetQuantity(pos.getNetQuantity() - quantity);
        pos.setLastUpdated(LocalDateTime.now());
        return positionRepository.save(pos);
    }
}
