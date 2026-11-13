package com.matvey.senlatest;

import com.matvey.senlatest.model.PriceRecord;
import com.matvey.senlatest.repository.PriceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:1/unused",
                "spring.datasource.username=unused",
                "spring.datasource.password=unused",
                "binance.base-url=http://localhost:1",
                "binance.symbol=BNBUSDT",
                "binance.poll-interval=PT24H"
        })
class PriceServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant T1 = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-01T11:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired
    private PriceRecordRepository repository;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void seedData() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new PriceRecord("BNBUSDT", new BigDecimal("600.00"), T1, T1),
                new PriceRecord("BNBUSDT", new BigDecimal("610.00"), T2, T2),
                new PriceRecord("BNBUSDT", new BigDecimal("620.00"), T3, T3)));
    }

    private String url(String path) {
        return "/api/v1/prices" + path;
    }

    @Test
    void latestReturnsMostRecentRecord() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/latest"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("symbol", "BNBUSDT")
                .containsEntry("fetchedAt", "2026-09-01T12:00:00Z");
        assertThat(new BigDecimal(String.valueOf(response.getBody().get("price"))))
                .isEqualByComparingTo("620.00");
    }

    @Test
    void averageReturnsDbComputedValue() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/average?from=2026-09-01T09:00:00Z&to=2026-09-01T13:00:00Z"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(String.valueOf(response.getBody().get("averagePrice"))))
                .isEqualByComparingTo("610.00");
    }

    @Test
    void paginatedListRespectsPageAndSize() {
        ResponseEntity<Map> firstPage = restTemplate.getForEntity(
                url("?from=2026-09-01T09:00:00Z&to=2026-09-01T13:00:00Z&page=0&size=2"), Map.class);

        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) firstPage.getBody().get("items")).hasSize(2);
        assertThat(firstPage.getBody())
                .containsEntry("page", 0)
                .containsEntry("size", 2)
                .containsEntry("totalElements", 3)
                .containsEntry("hasNext", true);

        ResponseEntity<Map> secondPage = restTemplate.getForEntity(
                url("?from=2026-09-01T09:00:00Z&to=2026-09-01T13:00:00Z&page=1&size=2"), Map.class);

        assertThat((List<?>) secondPage.getBody().get("items")).hasSize(1);
        assertThat(secondPage.getBody()).containsEntry("hasNext", false);
    }

    @Test
    void trendReturnsUpwardForRisingPrices() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/trend?from=2026-09-01T09:00:00Z&to=2026-09-01T13:00:00Z"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("symbol", "BNBUSDT")
                .containsEntry("trend", "UPWARD")
                .containsEntry("startDate", "2026-09-01T09:00:00Z")
                .containsEntry("endDate", "2026-09-01T13:00:00Z");
    }

    @Test
    void emptyRangeReturns404ForAverage() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/average?from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void invertedRangeReturns400() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/trend?from=2026-09-01T13:00:00Z&to=2026-09-01T09:00:00Z"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
    }
}
