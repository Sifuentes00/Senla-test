package com.matvey.senlatest.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceRecordResponse(
        String symbol,
        BigDecimal price,
        Instant fetchedAt
) {
}
