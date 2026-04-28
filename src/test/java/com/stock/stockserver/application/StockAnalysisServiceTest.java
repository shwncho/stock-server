package com.stock.stockserver.application;

import com.stock.stockserver.domain.entity.AnalysisJob;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.RecommendationStatus;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.AnalysisJobStore;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.external.LLMApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    private AnalysisResultSaveService analysisResultSaveService;

    private StockAnalysisService stockAnalysisService;

    @BeforeEach
    void setUp() {
        stockAnalysisService = new StockAnalysisService(
                dataCollectionService,
                llmApiClient,
                analysisResultRepository,
                analysisResultSaveService,
                jobStore,
                Runnable::run
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
    @DisplayName("getLatestAnalysis - 최근 분석 결과 조회")
    void getLatestAnalysis() {
        LLMAnalysisResult result1 = LLMAnalysisResult.builder()
                .stockCode("005930")
                .stockName("Samsung")
                .analysisDate(LocalDate.now())
                .llmAnalysis("Analysis text")
                .recommendation(com.stock.stockserver.domain.RecommendationStatus.BUY)
                .analysisId("test-id")
                .build();

        when(analysisResultRepository.findTop20ByAnalysisDateOrderByCreatedAtDesc(any()))
                .thenReturn(Arrays.asList(result1));

        List<AnalysisResultDto> results = stockAnalysisService.getLatestAnalysis();

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @ParameterizedTest
    @MethodSource("analysisTargets")
    @DisplayName("runFullAnalysisInternal - 분석 대상별 데이터 수집과 LLM 분석")
    void runFullAnalysisInternal_byTarget(AnalysisTarget target) {
        StockDataDto stockData = stockData(target, "005930", "Samsung");
        when(dataCollectionService.collectStockData(target)).thenReturn(List.of(stockData));
        when(llmApiClient.analyzeStock(stockData)).thenReturn(LLMAnalysisResponseDto.builder()
                .recommendation(RecommendationStatus.BUY)
                .confidence(0.8)
                .summary("summary")
                .fullAnalysis("analysis")
                .build());

        List<LLMAnalysisResult> results = stockAnalysisService.runFullAnalysisInternal("analysis-id", target);

        assertEquals(1, results.size());
        assertEquals(target, results.get(0).getTarget());
        verify(dataCollectionService).collectStockData(target);
        verify(analysisResultSaveService).saveAll(any());
    }

    @ParameterizedTest
    @MethodSource("analysisTargets")
    @DisplayName("runFullAnalysis - 분석 대상별 작업 완료 상태 저장")
    void runFullAnalysis_byTarget(AnalysisTarget target) {
        when(dataCollectionService.collectStockData(target)).thenReturn(List.of());

        stockAnalysisService.runFullAnalysis("analysis-id", target);

        verify(dataCollectionService).collectStockData(target);
        verify(jobStore).save(argThat(job ->
                "analysis-id".equals(job.getAnalysisId())
                        && job.getStatus() == AnalysisStatus.DONE
        ));
    }

    private StockDataDto stockData(AnalysisTarget target, String stockCode, String stockName) {
        return StockDataDto.builder()
                .target(target)
                .exchangeCode(target == AnalysisTarget.OVERSEAS ? "NAS" : "KRX")
                .stockCode(stockCode)
                .stockName(stockName)
                .currentPrice(BigDecimal.valueOf(100))
                .changePercent(BigDecimal.ONE)
                .tradingVolume(1000L)
                .tradingAmount(100000L)
                .priceHigh52Week(BigDecimal.valueOf(120))
                .priceLow52Week(BigDecimal.valueOf(80))
                .analysisDate(LocalDate.now())
                .dailyPricesJson("[]")
                .build();
    }

    private static Stream<AnalysisTarget> analysisTargets() {
        return Stream.of(AnalysisTarget.DOMESTIC, AnalysisTarget.OVERSEAS, AnalysisTarget.ALL);
    }
}
