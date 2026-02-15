package com.stock.stockserver.dto;

import com.stock.stockserver.domain.RecommendationStatus;
import lombok.Builder;

@Builder
public record LLMAnalysisResponseDto(
        RecommendationStatus recommendation,
        Double confidence,
        String summary,
        String fullAnalysis
) {
}