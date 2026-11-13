package com.matvey.senlatest.service;

import com.matvey.senlatest.client.BinanceAvgPriceResponse;
import com.matvey.senlatest.client.BinanceClient;
import com.matvey.senlatest.config.BinanceProperties;
import com.matvey.senlatest.dto.AveragePriceResponse;
import com.matvey.senlatest.dto.PricePageResponse;
import com.matvey.senlatest.dto.PriceRecordResponse;
import com.matvey.senlatest.dto.TrendResponse;
import com.matvey.senlatest.exception.ResourceNotFoundException;
import com.matvey.senlatest.mapper.PriceRecordMapper;
import com.matvey.senlatest.model.PriceRecord;
import com.matvey.senlatest.model.Trend;
import com.matvey.senlatest.repository.PriceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class PriceService {

    private static final BigDecimal FLAT_THRESHOLD = new BigDecimal("0.001");

    private final BinanceClient binanceClient;
    private final PriceRecordRepository repository;
    private final BinanceProperties properties;
    private final PriceRecordMapper mapper;

    public PriceService(BinanceClient binanceClient,
                        PriceRecordRepository repository,
                        BinanceProperties properties,
                        PriceRecordMapper mapper) {
        this.binanceClient = binanceClient;
        this.repository = repository;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void pollAndStore() {
        String symbol = properties.symbol();

        BinanceAvgPriceResponse response = binanceClient.fetchAvgPrice(symbol);
        Instant closeTime = Instant.ofEpochMilli(response.closeTime());

        if (repository.existsBySymbolAndCloseTime(symbol, closeTime)) {
            log.debug("Skipping duplicate price record: symbol={}, closeTime={}", symbol, closeTime);
            return;
        }

        PriceRecord record = new PriceRecord(symbol, response.price(), Instant.now(), closeTime);
        repository.save(record);
        log.info("Saved price record: symbol={}, price={}, closeTime={}",
                symbol, response.price(), closeTime);
    }

    public PriceRecordResponse getLatest() {
        PriceRecord record = repository
                .findTopBySymbolOrderByFetchedAtDesc(properties.symbol())
                .orElseThrow(() -> new ResourceNotFoundException("No price records yet"));

        return mapper.toResponse(record);
    }

    public AveragePriceResponse getAverage(Instant from, Instant to) {
        validateRange(from, to);

        BigDecimal average = repository
                .findAveragePriceBySymbolAndFetchedAtBetween(properties.symbol(), from, to)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No price records in range " + from + " - " + to));

        return new AveragePriceResponse(average);
    }

    public PricePageResponse getPage(Instant from, Instant to, Pageable pageable) {
        validateRange(from, to);

        Page<PriceRecord> page = repository
                .findBySymbolAndFetchedAtBetween(properties.symbol(), from, to, pageable);

        return mapper.toPageResponse(page);
    }

    public TrendResponse getTrend(Instant from, Instant to) {
        validateRange(from, to);
        String symbol = properties.symbol();

        List<PriceRecord> records = repository
                .findBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(symbol, from, to);

        if (records.isEmpty()) {
            throw new ResourceNotFoundException("No price records in range " + from + " - " + to);
        }

        BigDecimal first = records.getFirst().getPrice();
        BigDecimal last = records.getLast().getPrice();
        Trend trend = calculateTrend(first, last);

        return new TrendResponse(symbol, from, to, trend);
    }

    private void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
    }

    private Trend calculateTrend(BigDecimal first, BigDecimal last) {
        BigDecimal relativeChange = last.subtract(first)
                .divide(first, 10, java.math.RoundingMode.HALF_UP);

        if (relativeChange.abs().compareTo(FLAT_THRESHOLD) < 0) {
            return Trend.FLAT;
        }
        return relativeChange.signum() > 0 ? Trend.UPWARD : Trend.DOWNWARD;
    }
}
