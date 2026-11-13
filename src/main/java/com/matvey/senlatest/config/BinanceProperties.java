package com.matvey.senlatest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        String baseUrl,
        String symbol,
        Duration pollInterval
) {
}
