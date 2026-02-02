package com.stock.stockserver.domain;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AnalysisJob {
    private String analysisId;
    private AnalysisStatus status;
    private List<LLMAnalysisResult> results;
    private String errorMessage;
}
