package com.stock.stockserver.dto;

import com.stock.stockserver.domain.AnalysisTarget;
import lombok.Builder;

@Builder
public record VolumeRankDto(
        AnalysisTarget target,
        String exchangeCode,
        String stockCode,
        String stockName,
        Double currentPrice,
        Double changePercent,
        Long tradingVolume,
        Long tradingAmount,
        Integer rank
) {
}
