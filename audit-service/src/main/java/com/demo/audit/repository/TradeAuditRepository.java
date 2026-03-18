package com.demo.audit.repository;

import com.demo.audit.entity.TradeAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeAuditRepository extends JpaRepository<TradeAuditEntity, Long> {

    List<TradeAuditEntity> findByAccountIdOrderByReceivedAtDesc(String accountId);

    @Query("SELECT a.kafkaPartition, COUNT(a) FROM TradeAuditEntity a GROUP BY a.kafkaPartition")
    List<Object[]> countByPartition();

    @Query("SELECT a.symbol, COUNT(a) FROM TradeAuditEntity a GROUP BY a.symbol ORDER BY COUNT(a) DESC")
    List<Object[]> countBySymbol();

    @Query("SELECT a.side, COUNT(a) FROM TradeAuditEntity a GROUP BY a.side")
    List<Object[]> countBySide();

    // Joins with trades table — execution-service writes trades, audit-service reads for analytics
    @Query(value = """
            SELECT t.status, COUNT(*)
            FROM trade_audit a
            LEFT JOIN trades t ON a.trade_id = t.trade_id
            GROUP BY t.status
            """, nativeQuery = true)
    List<Object[]> countByTradeStatus();
}
