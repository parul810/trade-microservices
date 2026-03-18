package com.demo.auth;

import com.demo.commons.security.JwtFilter;
import com.demo.commons.security.JwtService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// Import only what's needed from trade-commons — avoids pulling in RedisService
// which requires StringRedisTemplate (not needed in auth-service)
@SpringBootApplication
@Import({JwtService.class, JwtFilter.class})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
