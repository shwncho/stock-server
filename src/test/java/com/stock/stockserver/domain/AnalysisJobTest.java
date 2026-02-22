package com.stock.stockserver.domain;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisJobTest {

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - RUNNING 상태")
    void createAnalysisJob_running() {
        AnalysisJob job = new AnalysisJob(
                "test-analysis-id",
                AnalysisStatus.RUNNING,
                null,
                null
        );

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.RUNNING, job.getStatus());
        assertNull(job.getResults());
        assertNull(job.getErrorMessage());
    }

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - DONE 상태")
    void createAnalysisJob_done() {
        LLMAnalysisResult result = LLMAnalysisResult.builder()
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .analysisDate(LocalDate.now())
                .llmAnalysis("Test analysis")
                .recommendation(RecommendationStatus.BUY)
                .build();

        AnalysisJob job = new AnalysisJob(
                "test-analysis-id",
                AnalysisStatus.DONE,
                Arrays.asList(result),
                null
        );

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.DONE, job.getStatus());
        assertNotNull(job.getResults());
        assertEquals(1, job.getResults().size());
        assertNull(job.getErrorMessage());
    }

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - FAILED 상태")
    void createAnalysisJob_failed() {
        AnalysisJob job = new AnalysisJob(
                "test-analysis-id",
                AnalysisStatus.FAILED,
                null,
                "Network error"
        );

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.FAILED, job.getStatus());
        assertNull(job.getResults());
        assertEquals("Network error", job.getErrorMessage());
    }
}
