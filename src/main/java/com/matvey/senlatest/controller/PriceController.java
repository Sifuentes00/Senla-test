package com.matvey.senlatest.controller;

import com.matvey.senlatest.dto.AveragePriceResponse;
import com.matvey.senlatest.dto.PricePageResponse;
import com.matvey.senlatest.dto.PriceRecordResponse;
import com.matvey.senlatest.dto.TrendResponse;
import com.matvey.senlatest.service.PriceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/latest")
    public PriceRecordResponse getLatest() {
        return priceService.getLatest();
    }

    @GetMapping("/average")
    public AveragePriceResponse getAverage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return priceService.getAverage(from, to);
    }

    @GetMapping
    public PricePageResponse getPrices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "fetchedAt") Pageable pageable) {
        return priceService.getPage(from, to, pageable);
    }

    @GetMapping("/trend")
    public TrendResponse getTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return priceService.getTrend(from, to);
    }
}
