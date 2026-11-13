package com.matvey.senlatest.dto;

import com.matvey.senlatest.model.Trend;

import java.time.Instant;

public record TrendResponse(
        String symbol,
        Instant startDate,
        Instant endDate,
        Trend trend
) {
}
