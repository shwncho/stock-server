package com.stock.stockserver.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class StockData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal changePercent;

    @Column(nullable = false)
    private Long tradingVolume;

    @Column(nullable = false)
    private Long tradingAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceHigh52Week;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceLow52Week;

    @Column(nullable = false)
    private LocalDate analysisDate;

    @Column(columnDefinition = "LONGTEXT")
    private String dailyPricesJson;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

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
    }
}
