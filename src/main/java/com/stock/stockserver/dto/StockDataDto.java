package com.stock.stockserver.dto;

import com.stock.stockserver.domain.AnalysisTarget;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StockDataDto(
        AnalysisTarget target,
        String exchangeCode,
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changePercent,
        Long tradingVolume,
        Long tradingAmount,
        BigDecimal priceHigh52Week,
        BigDecimal priceLow52Week,
        LocalDate analysisDate,
        String dailyPricesJson
) {
}
