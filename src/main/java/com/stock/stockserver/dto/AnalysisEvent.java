package com.stock.stockserver.dto;

import com.stock.stockserver.domain.AnalysisTarget;

import java.time.LocalDateTime;

public record AnalysisEvent(
        String analysisId,
        AnalysisTarget target,
        LocalDateTime requestedAt
) {
    public static AnalysisEvent of(String analysisId) {
        return of(analysisId, AnalysisTarget.ALL);
    }

    public static AnalysisEvent of(String analysisId, AnalysisTarget target) {
        return new AnalysisEvent(analysisId, target, LocalDateTime.now());
    }

    public AnalysisTarget resolvedTarget() {
        return target == null ? AnalysisTarget.ALL : target;
    }
}
