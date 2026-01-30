package com.stock.stockserver.dto;

import lombok.Builder;

@Builder
public record VolumeRankDto(
        String stockCode,
        String stockName,
        Double currentPrice,
        Double changePercent,
        Long tradingVolume,
        Long tradingAmount,
        Integer rank
) {
}
