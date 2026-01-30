package com.stock.stockserver.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_prices", indexes = {
        @Index(name = "idx_stock_code_date", columnList = "stockCode,tradeDate")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal highPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal lowPrice;

    @Column(nullable = false)
    private Long volume;

    @Builder
    private DailyPrice(String stockCode, LocalDate tradeDate,
                       BigDecimal openPrice, BigDecimal closePrice,
                       BigDecimal highPrice, BigDecimal lowPrice, Long volume) {
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }
}
