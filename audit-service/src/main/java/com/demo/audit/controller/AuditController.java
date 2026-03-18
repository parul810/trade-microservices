package com.demo.audit.controller;

import com.demo.audit.entity.TradeAuditEntity;
import com.demo.audit.repository.TradeAuditRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final TradeAuditRepository tradeAuditRepository;

    public AuditController(TradeAuditRepository tradeAuditRepository) {
        this.tradeAuditRepository = tradeAuditRepository;
    }

    @GetMapping("/trades/{accountId}")
    public ResponseEntity<List<TradeAuditEntity>> getByAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(tradeAuditRepository.findByAccountIdOrderByReceivedAtDesc(accountId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalTradesReceived", tradeAuditRepository.count());

        Map<String, Long> byPartition = new LinkedHashMap<>();
        for (Object[] row : tradeAuditRepository.countByPartition()) {
            byPartition.put("partition-" + row[0], ((Number) row[1]).longValue());
        }
        stats.put("byPartition", byPartition);

        Map<String, Long> bySymbol = new LinkedHashMap<>();
        for (Object[] row : tradeAuditRepository.countBySymbol()) {
            bySymbol.put((String) row[0], ((Number) row[1]).longValue());
        }
        stats.put("bySymbol", bySymbol);

        Map<String, Long> bySide = new LinkedHashMap<>();
        for (Object[] row : tradeAuditRepository.countBySide()) {
            bySide.put((String) row[0], ((Number) row[1]).longValue());
        }
        stats.put("bySide", bySide);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : tradeAuditRepository.countByTradeStatus()) {
            String status = row[0] != null ? (String) row[0] : "UNPROCESSED";
            byStatus.put(status, ((Number) row[1]).longValue());
        }
        stats.put("byStatus", byStatus);

        stats.put("consumerGroup", "audit-group");

        return ResponseEntity.ok(stats);
    }
}
