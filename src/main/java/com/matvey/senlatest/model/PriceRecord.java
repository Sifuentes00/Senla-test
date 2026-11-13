package com.matvey.senlatest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String symbol;

    @Column(nullable = false, precision = 38, scale = 18, updatable = false)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt;

    @Column(name = "close_time", nullable = false, updatable = false)
    private Instant closeTime;

    public PriceRecord(String symbol, BigDecimal price, Instant fetchedAt, Instant closeTime) {
        this.symbol = symbol;
        this.price = price;
        this.fetchedAt = fetchedAt;
        this.closeTime = closeTime;
    }
}
