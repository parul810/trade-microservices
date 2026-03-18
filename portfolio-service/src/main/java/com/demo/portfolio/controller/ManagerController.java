package com.demo.portfolio.controller;

import com.demo.commons.redis.RedisService;
import com.demo.portfolio.model.PortfolioManager;
import com.demo.portfolio.model.PortfolioResponse;
import com.demo.portfolio.service.ManagerService;
import com.demo.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/managers")
public class ManagerController {

    private final ManagerService managerService;
    private final PortfolioService portfolioService;
    private final RedisService redisService;

    public ManagerController(ManagerService managerService,
                             PortfolioService portfolioService,
                             RedisService redisService) {
        this.managerService   = managerService;
        this.portfolioService = portfolioService;
        this.redisService     = redisService;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioManager>> getAllManagers() {
        return ResponseEntity.ok(managerService.getAllManagers());
    }

    @GetMapping("/{managerId}")
    public ResponseEntity<PortfolioManager> getManager(@PathVariable("managerId") String managerId) {
        if (!ManagerService.MANAGER_NAMES.containsKey(managerId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(managerService.getManager(managerId));
    }

    @GetMapping("/{managerId}/portfolio")
    public ResponseEntity<List<PortfolioResponse>> getManagerPortfolios(
            @PathVariable("managerId") String managerId) {
        Set<String> accountIds = redisService.getManagerAccounts(managerId);
        if (accountIds == null || accountIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<PortfolioResponse> portfolios = new ArrayList<>();
        for (String accountId : accountIds.stream().sorted().toList()) {
            try {
                portfolios.add(portfolioService.computePortfolio(accountId));
            } catch (Exception e) {
                // Skip accounts that fail — don't break the whole response
            }
        }
        return ResponseEntity.ok(portfolios);
    }

    @GetMapping("/{managerId}/accounts")
    public ResponseEntity<List<Map<String, Object>>> getManagerAccounts(
            @PathVariable("managerId") String managerId) {
        Set<String> accountIds = redisService.getManagerAccounts(managerId);
        if (accountIds == null || accountIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> accounts = accountIds.stream()
                .sorted()
                .map(accountId -> {
                    Set<String> symbols = redisService.getAccountSymbols(accountId);
                    return Map.<String, Object>of(
                            "accountId",   accountId,
                            "managerId",   managerId,
                            "symbolCount", symbols != null ? symbols.size() : 0
                    );
                })
                .toList();

        return ResponseEntity.ok(accounts);
    }
}
