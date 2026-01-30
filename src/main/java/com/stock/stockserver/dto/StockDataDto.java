package com.stock.stockserver.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StockDataDto(
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
