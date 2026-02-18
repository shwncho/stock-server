package com.stock.stockserver.dto;

import java.time.LocalDateTime;

public record AnalysisEvent(
        String analysisId,
        LocalDateTime requestedAt
) {
    public static AnalysisEvent of(String analysisId) {
        return new AnalysisEvent(analysisId, LocalDateTime.now());
    }
}
