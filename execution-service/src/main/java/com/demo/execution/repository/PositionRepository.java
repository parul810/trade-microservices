package com.demo.execution.repository;

import com.demo.execution.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<PositionEntity, PositionEntity.PositionId> {
    Optional<PositionEntity> findByAccountIdAndSymbol(String accountId, String symbol);
    List<PositionEntity> findByAccountId(String accountId);
}
