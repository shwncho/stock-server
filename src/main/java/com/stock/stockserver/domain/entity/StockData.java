package com.stock.stockserver.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "stock_data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    // 현재 시세 정보
    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal changePercent;

    @Column(nullable = false)
    private Long tradingVolume;

    @Column(nullable = false)
    private Long tradingAmount;

    // 고저가 정보 (52주 기준)
    @Column(precision = 10, scale = 2)
    private BigDecimal priceHigh52Week;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceLow52Week;

    @Column(nullable = false)
    private LocalDate analysisDate;

    // JSON 형식의 일봉 데이터 저장
    @Column(columnDefinition = "LONGTEXT")
    private String dailyPricesJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private StockData(String stockCode, String stockName, BigDecimal currentPrice,
                      BigDecimal changePercent, Long tradingVolume, Long tradingAmount,
                      BigDecimal priceHigh52Week, BigDecimal priceLow52Week,
                      LocalDate analysisDate, String dailyPricesJson) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.tradingVolume = tradingVolume;
        this.tradingAmount = tradingAmount;
        this.priceHigh52Week = priceHigh52Week;
        this.priceLow52Week = priceLow52Week;
        this.analysisDate = analysisDate;
        this.dailyPricesJson = dailyPricesJson;
        this.createdAt = LocalDateTime.now();
    }
}
