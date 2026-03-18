package com.demo.execution.repository;

import com.demo.execution.entity.TradeOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOutboxRepository extends JpaRepository<TradeOutboxEntry, Long> {

    // Poller fetches up to 100 unpublished entries per tick, ordered oldest-first
    // to preserve approximate publication order.
    List<TradeOutboxEntry> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
