package com.stock.stockserver.domain;

import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.RecommendationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisStatusTest {

    @Test
    @DisplayName("AnalysisStatus enum 값 확인")
    void analysisStatus_values() {
        assertEquals(3, AnalysisStatus.values().length);
        assertNotNull(AnalysisStatus.RUNNING);
        assertNotNull(AnalysisStatus.DONE);
        assertNotNull(AnalysisStatus.FAILED);
    }
}
