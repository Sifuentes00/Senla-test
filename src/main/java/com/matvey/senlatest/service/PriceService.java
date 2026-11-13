package com.matvey.senlatest.service;

import com.matvey.senlatest.client.BinanceAvgPriceResponse;
import com.matvey.senlatest.client.BinanceClient;
import com.matvey.senlatest.config.BinanceProperties;
import com.matvey.senlatest.model.PriceRecord;
import com.matvey.senlatest.repository.PriceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class PriceService {

    private final BinanceClient binanceClient;
    private final PriceRecordRepository repository;
    private final BinanceProperties properties;

    public PriceService(BinanceClient binanceClient,
                        PriceRecordRepository repository,
                        BinanceProperties properties) {
        this.binanceClient = binanceClient;
        this.repository = repository;
        this.properties = properties;
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
}
