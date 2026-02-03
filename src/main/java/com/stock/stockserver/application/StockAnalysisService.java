package com.stock.stockserver.application;

import com.stock.stockserver.domain.AnalysisJob;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.AnalysisJobStore;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.external.LLMApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockDataCollectionService dataCollectionService;
    private final LLMApiClient llmApiClient;
    private final LLMAnalysisResultRepository analysisResultRepository;
    private final AnalysisJobStore jobStore;

    @Async
    @Transactional
    public void runFullAnalysisAsync(String analysisId) {
        try {
            List<LLMAnalysisResult> results = runFullAnalysis();
            jobStore.save(new AnalysisJob(
                    analysisId,
                    AnalysisStatus.DONE,
                    results,
                    null
            ));
        } catch (Exception e) {
            jobStore.save(new AnalysisJob(
                    analysisId,
                    AnalysisStatus.FAILED,
                    null,
                    e.getMessage()
            ));
        }
    }

    /**
     * 전체 분석 프로세스 실행
     */
    @Transactional
    public List<LLMAnalysisResult> runFullAnalysis() {
        log.info("\n");
        log.info("=====================================");
        log.info("  KIS Stock Analysis with LLM");
        log.info("=====================================\n");

        // 1단계: 데이터 수집
        List<StockDataDto> stockDataList = dataCollectionService.collectStockData();

        // 2단계: LLM 분석
        List<LLMAnalysisResult> results = new ArrayList<>();

        for (StockDataDto stockData : stockDataList) {
            try {
                log.info("LLM 분석 요청: {} ({})", stockData.stockName(), stockData.stockCode());

                String analysis = llmApiClient.analyzeStock(stockData);

                if (analysis != null) {
                    LLMAnalysisResult result = LLMAnalysisResult.builder()
                            .stockCode(stockData.stockCode())
                            .stockName(stockData.stockName())
                            .analysisDate(LocalDate.now())
                            .llmAnalysis(analysis)
                            .recommendation(extractRecommendation(analysis))
                            .build();

                    analysisResultRepository.save(result);
                    results.add(result);

                    log.info("분석 완료: {}", stockData.stockCode());
                }
            } catch (Exception e) {
                log.error("LLM 분석 실패: {}", stockData.stockCode(), e);
            }
        }

        // 3단계: 결과 출력
        generateReport(results);

        return results;
    }

    /**
     * 분석 결과에서 추천 추출
     */
    private String extractRecommendation(String analysis) {
        if (analysis.contains("매수") || analysis.contains("BUY")) {
            return "BUY";
        } else if (analysis.contains("매도") || analysis.contains("SELL")) {
            return "SELL";
        } else {
            return "HOLD";
        }
    }

    /**
     * 최종 리포트 생성
     */
    private void generateReport(List<LLMAnalysisResult> results) {
        log.info("\n=====================================");
        log.info("        분석 결과 리포트");
        log.info("=====================================\n");

        for (LLMAnalysisResult result : results) {
            log.info("[{}] {}", result.getStockCode(), result.getStockName());
            log.info("분석 의견: {}", result.getRecommendation());
            log.info("상세 분석:");
            log.info(result.getLlmAnalysis());
            log.info("-------------------------------------\n");
        }

        log.info("=====================================\n");
    }

    /**
     * 최근 분석 결과 조회
     */
    public List<AnalysisResultDto> getLatestAnalysis() {
        List<LLMAnalysisResult> results = analysisResultRepository.findTop10ByAnalysisDateOrderByCreatedAtDesc(LocalDate.now());
        return results.stream()
                .map(AnalysisResultDto::from)
                .toList();
    }

    public void saveJob(String analysisId) {
        jobStore.save(new AnalysisJob(
                analysisId,
                AnalysisStatus.RUNNING,
                null,
                null)
        );
    }

    public AnalysisJob getAnalysisJob(String analysisId) {
        return jobStore.get(analysisId);
    }
}
