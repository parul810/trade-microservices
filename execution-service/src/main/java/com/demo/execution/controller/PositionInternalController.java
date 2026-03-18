package com.demo.execution.controller;

import com.demo.commons.model.dto.PositionDto;
import com.demo.execution.entity.PositionEntity;
import com.demo.execution.repository.PositionRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal HTTP API consumed by portfolio-service.
 * Not intended for external clients — portfolio-service reads positions
 * from Redis cache first; it calls this endpoint only on cache miss.
 */
@RestController
@RequestMapping("/internal/positions")
public class PositionInternalController {

    private final PositionRepository positionRepository;

    public PositionInternalController(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @GetMapping("/{accountId}")
    public List<PositionDto> getPositions(@PathVariable("accountId") String accountId) {
        List<PositionEntity> positions = positionRepository.findByAccountId(accountId);
        return positions.stream()
                .map(p -> new PositionDto(p.getSymbol(), p.getNetQuantity(), p.getAvgCost()))
                .toList();
    }
}
