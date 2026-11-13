package com.matvey.senlatest.scheduler;

import com.matvey.senlatest.service.PriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PricePollingScheduler {

    private final PriceService priceService;

    public PricePollingScheduler(PriceService priceService) {
        this.priceService = priceService;
    }

    @Scheduled(fixedDelayString = "${binance.poll-interval}")
    public void poll() {
        try {
            priceService.pollAndStore();
        } catch (Exception e) {
            log.warn("Price polling tick failed, schedule continues: {}", e.getMessage());
        }
    }
}
