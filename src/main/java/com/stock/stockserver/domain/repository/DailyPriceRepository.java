package com.stock.stockserver.domain.repository;

import com.stock.stockserver.domain.entity.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    List<DailyPrice> findByStockCodeOrderByTradeDateDesc(String stockCode);

    List<DailyPrice> findByStockCodeAndTradeDateBetween(String stockCode, LocalDate startDate, LocalDate endDate);
}
