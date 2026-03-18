package com.demo.portfolio.service;

import com.demo.commons.redis.RedisService;
import com.demo.portfolio.model.PortfolioManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ManagerService {

    public static final Map<String, String> MANAGER_NAMES = Map.of(
            "PM-001", "Sarah Chen",
            "PM-002", "James Park",
            "PM-003", "Priya Nair",
            "PM-004", "Marcus Webb",
            "PM-005", "Li Wei",
            "PM-006", "Fatima Al-Syed",
            "PM-007", "Tom Brennan",
            "PM-008", "Yuki Tanaka",
            "PM-009", "Elena Rossi",
            "PM-010", "David Okafor"
    );

    private final RedisService redisService;

    public ManagerService(RedisService redisService) {
        this.redisService = redisService;
    }

    public PortfolioManager getManager(String managerId) {
        String name = MANAGER_NAMES.getOrDefault(managerId, "Unknown");
        Set<String> accounts = redisService.getManagerAccounts(managerId);
        List<String> accountList = accounts != null ? new ArrayList<>(accounts) : List.of();
        accountList.sort(String::compareTo);
        return new PortfolioManager(managerId, name, accountList);
    }

    public List<PortfolioManager> getAllManagers() {
        List<PortfolioManager> managers = new ArrayList<>();
        for (String managerId : MANAGER_NAMES.keySet().stream().sorted().toList()) {
            managers.add(getManager(managerId));
        }
        return managers;
    }

    public String getManagerName(String managerId) {
        return MANAGER_NAMES.getOrDefault(managerId, "Unknown");
    }
}
