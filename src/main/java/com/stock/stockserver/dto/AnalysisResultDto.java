package com.stock.stockserver.dto;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import jakarta.persistence.Column;

import java.time.LocalDate;

public record AnalysisResultDto(
        String stockCode,
        String stockName,
        LocalDate analysisDate,
        String llmAnalysis,
        String recommendation
) {

    public static AnalysisResultDto from(LLMAnalysisResult llmAnalysisResult) {
        return new AnalysisResultDto(
                llmAnalysisResult.getStockCode(),
                llmAnalysisResult.getStockName(),
                llmAnalysisResult.getAnalysisDate(),
                llmAnalysisResult.getLlmAnalysis(),
                llmAnalysisResult.getRecommendation()
        );
    }
}
