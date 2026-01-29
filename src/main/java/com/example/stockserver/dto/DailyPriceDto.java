package com.example.stockserver.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record DailyPriceDto(
        String stockCode,
        LocalDate tradeDate,
        Double openPrice,
        Double closePrice,
        Double highPrice,
        Double lowPrice,
        Long volume
) {
}
