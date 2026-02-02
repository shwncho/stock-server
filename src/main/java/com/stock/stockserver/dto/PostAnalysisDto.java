package com.stock.stockserver.dto;

import com.stock.stockserver.domain.AnalysisStatus;

public record PostAnalysisDto(
        String analysisId,
        AnalysisStatus status
) {
}
