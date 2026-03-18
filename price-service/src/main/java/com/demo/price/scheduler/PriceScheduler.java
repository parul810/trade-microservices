package com.demo.price.scheduler;

import com.demo.price.producer.PriceProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceScheduler.class);

    private final PriceProducer priceProducer;

    @Value("${app.price-poll-interval-ms}")
    private long pollIntervalMs;

    public PriceScheduler(PriceProducer priceProducer) {
        this.priceProducer = priceProducer;
    }

    @Scheduled(fixedDelayString = "${app.price-poll-interval-ms}", initialDelay = 5000)
    public void run() {
        log.info("PriceScheduler triggered");
        priceProducer.fetchAndPublishAll();
    }
}
