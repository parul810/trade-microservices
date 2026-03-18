package com.demo.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scans com.demo.commons to pick up RedisService and JwtService from trade-commons
@SpringBootApplication(scanBasePackages = {"com.demo.trade", "com.demo.commons"})
@EnableScheduling
public class TradeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeServiceApplication.class, args);
    }
}
