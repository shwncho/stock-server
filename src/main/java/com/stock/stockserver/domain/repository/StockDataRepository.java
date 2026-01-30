package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.entity.StockData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockDataRepository extends JpaRepository<StockData, Long> {
    Optional<StockData> findByStockCodeAndAnalysisDate(String stockCode, LocalDate analysisDate);

    List<StockData> findByAnalysisDateOrderByTradingVolumeDesc(LocalDate analysisDate);
}
