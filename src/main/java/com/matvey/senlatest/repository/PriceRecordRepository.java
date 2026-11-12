package com.matvey.senlatest.repository;

import com.matvey.senlatest.model.PriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    Optional<PriceRecord> findTopBySymbolOrderByFetchedAtDesc(String symbol);

    boolean existsBySymbolAndCloseTime(String symbol, Instant closeTime);

    List<PriceRecord> findBySymbolAndFetchedAtBetweenOrderByFetchedAtAsc(
            String symbol, Instant from, Instant to);

    Page<PriceRecord> findBySymbolAndFetchedAtBetween(
            String symbol, Instant from, Instant to, Pageable pageable);

    @Query("SELECT AVG(p.price) FROM PriceRecord p " +
            "WHERE p.symbol = :symbol AND p.fetchedAt BETWEEN :from AND :to")
    Optional<BigDecimal> findAveragePriceBySymbolAndFetchedAtBetween(
            @Param("symbol") String symbol,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
