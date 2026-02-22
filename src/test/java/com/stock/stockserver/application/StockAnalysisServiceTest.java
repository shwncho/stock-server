package com.stock.stockserver.application;

import com.stock.stockserver.domain.AnalysisJob;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.AnalysisJobStore;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.infrastructure.external.LLMApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAnalysisServiceTest {

    @Mock
    private StockDataCollectionService dataCollectionService;

    @Mock
    private LLMApiClient llmApiClient;

    @Mock
    private LLMAnalysisResultRepository analysisResultRepository;

    @Mock
    private AnalysisJobStore jobStore;

    @Mock
    private Executor llmApiExecutor;

    private StockAnalysisService stockAnalysisService;

    @BeforeEach
    void setUp() {
        stockAnalysisService = new StockAnalysisService(
                dataCollectionService,
                llmApiClient,
                analysisResultRepository,
                jobStore,
                llmApiExecutor
        );
    }

    @Test
    @DisplayName("saveJob - 분석 작업 저장")
    void saveJob() {
        String analysisId = "test-analysis-id";

        stockAnalysisService.saveJob(analysisId);

        verify(jobStore, times(1)).save(any(AnalysisJob.class));
    }

    @Test
    @DisplayName("getAnalysisJob - 분석 작업 조회")
    void getAnalysisJob() {
        String analysisId = "test-analysis-id";
        AnalysisJob expectedJob = new AnalysisJob(analysisId, AnalysisStatus.RUNNING, null, null);
        when(jobStore.get(analysisId)).thenReturn(expectedJob);

        AnalysisJob result = stockAnalysisService.getAnalysisJob(analysisId);

        assertNotNull(result);
        assertEquals(analysisId, result.getAnalysisId());
        assertEquals(AnalysisStatus.RUNNING, result.getStatus());
    }

    @Test
    @DisplayName("getAnalysisJob - 작업이 없는 경우")
    void getAnalysisJob_notFound() {
        String analysisId = "non-existent-id";
        when(jobStore.get(analysisId)).thenReturn(null);

        AnalysisJob result = stockAnalysisService.getAnalysisJob(analysisId);

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestAnalysis - 최근 분석 결과 조회")
    void getLatestAnalysis() {
        LLMAnalysisResult result1 = LLMAnalysisResult.builder()
                .stockCode("005930")
                .stockName("Samsung")
                .analysisDate(LocalDate.now())
                .llmAnalysis("Analysis text")
                .recommendation(com.stock.stockserver.domain.RecommendationStatus.BUY)
                .build();

        when(analysisResultRepository.findTop10ByAnalysisDateOrderByCreatedAtDesc(any()))
                .thenReturn(Arrays.asList(result1));

        List<AnalysisResultDto> results = stockAnalysisService.getLatestAnalysis();

        assertNotNull(results);
        assertEquals(1, results.size());
    }
}
