package com.stock.stockserver.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_analysis_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LLMAnalysisResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false)
    private LocalDate analysisDate;

    // LLM의 분석 결과
    @Column(columnDefinition = "LONGTEXT")
    private String llmAnalysis;

    // 추천 (LLM이 텍스트로 생성)
    @Column(length = 500)
    private String recommendation;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private LLMAnalysisResult(String stockCode, String stockName, LocalDate analysisDate,
                              String llmAnalysis, String recommendation) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.analysisDate = analysisDate;
        this.llmAnalysis = llmAnalysis;
        this.recommendation = recommendation;
        this.createdAt = LocalDateTime.now();
    }
}
