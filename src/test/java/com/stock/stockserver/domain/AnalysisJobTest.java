package com.stock.stockserver.domain;

import com.stock.stockserver.domain.entity.AnalysisJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisJobTest {

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - RUNNING 상태")
    void createAnalysisJob_running() {
        AnalysisJob job = AnalysisJob.builder()
                .analysisId("test-analysis-id")
                .status(AnalysisStatus.RUNNING)
                .errorMessage(null)
                .build();

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.RUNNING, job.getStatus());
        assertNull(job.getErrorMessage());
    }

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - DONE 상태")
    void createAnalysisJob_done() {
        AnalysisJob job = AnalysisJob.builder()
                .analysisId("test-analysis-id")
                .status(AnalysisStatus.DONE)
                .errorMessage(null)
                .build();

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.DONE, job.getStatus());
        assertNull(job.getErrorMessage());
    }

    @Test
    @DisplayName("AnalysisJob 생성 테스트 - FAILED 상태")
    void createAnalysisJob_failed() {
        AnalysisJob job = AnalysisJob.builder()
                .analysisId("test-analysis-id")
                .status(AnalysisStatus.FAILED)
                .errorMessage("Network error")
                .build();

        assertEquals("test-analysis-id", job.getAnalysisId());
        assertEquals(AnalysisStatus.FAILED, job.getStatus());
        assertEquals("Network error", job.getErrorMessage());
    }

    @Test
    @DisplayName("AnalysisJob 상태 업데이트 테스트")
    void updateStatus() {
        AnalysisJob job = AnalysisJob.builder()
                .analysisId("test-analysis-id")
                .status(AnalysisStatus.RUNNING)
                .errorMessage(null)
                .build();

        job.updateStatus(AnalysisStatus.DONE);

        assertEquals(AnalysisStatus.DONE, job.getStatus());
    }

    @Test
    @DisplayName("AnalysisJob 상태 및 에러 메시지 업데이트 테스트")
    void updateStatusWithError() {
        AnalysisJob job = AnalysisJob.builder()
                .analysisId("test-analysis-id")
                .status(AnalysisStatus.RUNNING)
                .errorMessage(null)
                .build();

        job.updateStatusWithError(AnalysisStatus.FAILED, "Connection timeout");

        assertEquals(AnalysisStatus.FAILED, job.getStatus());
        assertEquals("Connection timeout", job.getErrorMessage());
    }
}
