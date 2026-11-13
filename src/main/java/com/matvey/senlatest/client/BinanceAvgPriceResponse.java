package com.matvey.senlatest.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAvgPriceResponse(
        BigDecimal price,
        long closeTime
) {
}
