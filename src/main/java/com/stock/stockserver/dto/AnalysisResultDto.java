package com.stock.stockserver.dto;

import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.domain.RecommendationStatus;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;

import java.time.LocalDate;

public record AnalysisResultDto(
        AnalysisTarget target,
        String stockCode,
        String stockName,
        LocalDate analysisDate,
        String llmAnalysis,
        RecommendationStatus recommendation
) {

    public static AnalysisResultDto from(LLMAnalysisResult llmAnalysisResult) {
        return new AnalysisResultDto(
                llmAnalysisResult.getTarget(),
                llmAnalysisResult.getStockCode(),
                llmAnalysisResult.getStockName(),
                llmAnalysisResult.getAnalysisDate(),
                llmAnalysisResult.getLlmAnalysis(),
                llmAnalysisResult.getRecommendation()
        );
    }
}
