package com.demo.portfolio.controller;

import com.demo.portfolio.model.PortfolioResponse;
import com.demo.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable("accountId") String accountId) {
        try {
            return ResponseEntity.ok(portfolioService.computePortfolio(accountId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
