package com.demo.portfolio.service;

import com.demo.portfolio.model.PortfolioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PortfolioPushService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPushService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final PortfolioService portfolioService;

    public PortfolioPushService(SimpMessagingTemplate messagingTemplate, PortfolioService portfolioService) {
        this.messagingTemplate = messagingTemplate;
        this.portfolioService  = portfolioService;
    }

    public void pushPortfolio(String accountId) {
        try {
            PortfolioResponse response = portfolioService.computePortfolio(accountId);
            messagingTemplate.convertAndSend("/topic/portfolio/" + accountId, response);
            log.debug("Portfolio pushed via WS | accountId={}", accountId);
        } catch (Exception e) {
            log.warn("Failed to push portfolio update | accountId={} reason={}", accountId, e.getMessage());
        }
    }
}
