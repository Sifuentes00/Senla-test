package com.matvey.senlatest.service;

import com.matvey.senlatest.client.BinanceAvgPriceResponse;
import com.matvey.senlatest.client.BinanceClient;
import com.matvey.senlatest.config.BinanceProperties;
import com.matvey.senlatest.dto.AveragePriceResponse;
import com.matvey.senlatest.dto.PricePageResponse;
import com.matvey.senlatest.dto.PriceRecordResponse;
import com.matvey.senlatest.dto.TrendResponse;
import com.matvey.senlatest.exception.BinanceApiException;
import com.matvey.senlatest.exception.ResourceNotFoundException;
import com.matvey.senlatest.mapper.PriceRecordMapper;
import com.matvey.senlatest.model.PriceRecord;
import com.matvey.senlatest.model.Trend;
import com.matvey.senlatest.repository.PriceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private PriceRecordRepository repository;

    private PriceService service;

    @BeforeEach
    void setUp() {
        BinanceProperties properties =
                new BinanceProperties("https://api.binance.com", SYMBOL, Duration.ofSeconds(30));
        service = new PriceService(binanceClient, repository, properties, new PriceRecordMapper());
    }

    private PriceRecord record(String price, Instant fetchedAt) {
        return new PriceRecord(SYMBOL, new BigDecimal(price), fetchedAt, fetchedAt);
    }

    @Nested
    class PollAndStore {

        @Test
        void savesRecordFromBinanceResponse() {
            long closeTimeMillis = 1_756_000_000_000L;
            when(binanceClient.fetchAvgPrice(SYMBOL))
                    .thenReturn(new BinanceAvgPriceResponse(new BigDecimal("600.50"), closeTimeMillis));
            when(repository.existsBySymbolAndCloseTime(SYMBOL, Instant.ofEpochMilli(closeTimeMillis)))
                    .thenReturn(false);

            service.pollAndStore();

            ArgumentCaptor<PriceRecord> captor = ArgumentCaptor.forClass(PriceRecord.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getSymbol()).isEqualTo(SYMBOL);
            assertThat(captor.getValue().getPrice()).isEqualByComparingTo("600.50");
            assertThat(captor.getValue().getCloseTime()).isEqualTo(Instant.ofEpochMilli(closeTimeMillis));
        }

        @Test
        void skipsDuplicateCloseTime() {
            when(binanceClient.fetchAvgPrice(SYMBOL))
                    .thenReturn(new BinanceAvgPriceResponse(new BigDecimal("600.50"), 1_756_000_000_000L));
            when(repository.existsBySymbolAndCloseTime(any(), any())).thenReturn(true);

            service.pollAndStore();

            verify(repository, never()).save(any());
        }

        @Test
        void propagatesBinanceFailureAndWritesNothing() {
            when(binanceClient.fetchAvgPrice(SYMBOL)).thenThrow(new BinanceApiException("down"));

            assertThatThrownBy(() -> service.pollAndStore())
                    .isInstanceOf(BinanceApiException.class);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    class GetLatest {

        @Test
        void returnsLatestRecord() {
            Instant fetchedAt = Instant.parse("2026-09-30T12:00:00Z");
            when(repository.findTopBySymbolOrderByFetchedAtDesc(SYMBOL))
                    .thenReturn(Optional.of(record("601.25", fetchedAt)));

            PriceRecordResponse response = service.getLatest();

            assertThat(response.symbol()).isEqualTo(SYMBOL);
            assertThat(response.price()).isEqualByComparingTo("601.25");
            assertThat(response.fetchedAt()).isEqualTo(fetchedAt);
        }

        @Test
        void throwsNotFoundWhenTableIsEmpty() {
            when(repository.findTopBySymbolOrderByFetchedAtDesc(SYMBOL))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatest())
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetAverage {

        @Test
        void returnsAverageForRange() {
            when(repository.findAveragePriceBySymbolAndFetchedAtBetween(SYMBOL, FROM, TO))
                    .thenReturn(Optional.of(new BigDecimal("10.2")));

            assertThat(service.getAverage(FROM, TO).averagePrice()).isEqualByComparingTo("10.2");
        }

        @Test
        void throwsNotFoundWhenRangeIsEmpty() {
            when(repository.findAveragePriceBySymbolAndFetchedAtBetween(SYMBOL, FROM, TO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAverage(FROM, TO))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> service.getAverage(TO, FROM))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(repository);
        }
    }

    @Nested
    class GetPage {

        @Test
        void mapsPageMetadataToResponse() {
            Pageable pageable = PageRequest.of(0, 20);
            when(repository.findBySymbolAndFetchedAtBetween(SYMBOL, FROM, TO, pageable))
                    .thenReturn(new PageImpl<>(List.of(record("600", FROM.plusSeconds(60))),
                            pageable, 21));

            PricePageResponse response = service.getPage(FROM, TO, pageable);

            assertThat(response.items()).hasSize(1);
            assertThat(response.page()).isZero();
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(21);
            assertThat(response.hasNext()).isTrue();
        }

        @Test
        void returnsEmptyPageWhenNoRecords() {
            Pageable pageable = PageRequest.of(0, 20);
            when(repository.findBySymbolAndFetchedAtBetween(SYMBOL, FROM, TO, pageable))
                    .thenReturn(Page.empty(pageable));

            PricePageResponse response = service.getPage(FROM, TO, pageable);

            assertThat(response.items()).isEmpty();
            assertThat(response.hasNext()).isFalse();
        }

        @Test
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> service.getPage(TO, FROM, PageRequest.of(0, 20)))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(repository);
        }
    }

    @Nested
    class GetTrend {

        private void stubRecords(String... prices) {
            List<PriceRecord> records = new ArrayList<>();
            for (int i = 0; i < prices.length; i++) {
                records.add(record(prices[i], FROM.plusSeconds(i * 60L)));
            }
            when(repository.findBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(SYMBOL, FROM, TO))
                    .thenReturn(records);
        }

        @Test
        void upwardWhenLastPriceIsHigher() {
            stubRecords("100", "105", "110");

            TrendResponse response = service.getTrend(FROM, TO);

            assertThat(response.trend()).isEqualTo(Trend.UPWARD);
            assertThat(response.symbol()).isEqualTo(SYMBOL);
            assertThat(response.startDate()).isEqualTo(FROM);
            assertThat(response.endDate()).isEqualTo(TO);
        }

        @Test
        void downwardWhenLastPriceIsLower() {
            stubRecords("110", "105", "100");

            assertThat(service.getTrend(FROM, TO).trend()).isEqualTo(Trend.DOWNWARD);
        }

        @Test
        void flatWhenChangeIsBelowThreshold() {
            // +0.05% < 0.1% flat threshold
            stubRecords("100", "100.05");

            assertThat(service.getTrend(FROM, TO).trend()).isEqualTo(Trend.FLAT);
        }

        @Test
        void upwardWhenChangeEqualsThreshold() {
            // exactly +0.1% is not flat (strict comparison)
            stubRecords("100", "100.1");

            assertThat(service.getTrend(FROM, TO).trend()).isEqualTo(Trend.UPWARD);
        }

        @Test
        void comparesOnlyFirstAndLastRecord() {
            // middle dip must not affect the result
            stubRecords("100", "10", "105");

            assertThat(service.getTrend(FROM, TO).trend()).isEqualTo(Trend.UPWARD);
        }

        @Test
        void throwsNotFoundWhenNoRecordsInRange() {
            when(repository.findBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(SYMBOL, FROM, TO))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.getTrend(FROM, TO))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> service.getTrend(TO, FROM))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(repository);
        }
    }
}
