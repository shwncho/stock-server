package com.stock.stockserver.application;

import com.stock.stockserver.domain.AnalysisJob;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.AnalysisJobStore;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.external.LLMApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockDataCollectionService dataCollectionService;
    private final LLMApiClient llmApiClient;
    private final LLMAnalysisResultRepository analysisResultRepository;
    private final AnalysisJobStore jobStore;
    private final Executor llmApiExecutor;

    @Async("analysisExecutor")
    public void runFullAnalysis(String analysisId) {
        try {
            List<LLMAnalysisResult> results = runFullAnalysisInternal();
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
     * 전체 분석 프로세스 실행 (병렬 처리)
     */
    @Transactional
    public List<LLMAnalysisResult> runFullAnalysisInternal() {
        log.info("\n");
        log.info("=====================================");
        log.info("  KIS Stock Analysis with LLM (병렬)");
        log.info("=====================================\n");

        // 1단계: 데이터 수집 (병렬 처리 포함)
        List<StockDataDto> stockDataList = dataCollectionService.collectStockData();

        // 2단계: LLM 분석 (제한된 병렬 처리)
        List<CompletableFuture<LLMAnalysisResult>> futures = stockDataList.stream()
                .map(stockData -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("LLM 분석 요청: {} ({})", stockData.stockName(), stockData.stockCode());

                        LLMAnalysisResponseDto analysisResponse = llmApiClient.analyzeStock(stockData);

                        if (analysisResponse != null) {
                            LLMAnalysisResult result = LLMAnalysisResult.builder()
                                    .stockCode(stockData.stockCode())
                                    .stockName(stockData.stockName())
                                    .analysisDate(LocalDate.now())
                                    .llmAnalysis(removeJsonBlock(analysisResponse.fullAnalysis()))
                                    .recommendation(analysisResponse.recommendation())
                                    .build();
                            log.info("분석 완료: {} ", stockData.stockCode());

                            return result;
                        }
                    } catch (Exception e) {
                        log.error("LLM 분석 실패: {}", stockData.stockCode(), e);
                    }
                    return null;
                }, llmApiExecutor))
                .collect(Collectors.toList());

        // 모든 병렬 작업 완료 대기 및 결과 수집
        List<LLMAnalysisResult> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 3단계: DB 배치 저장
        if (!results.isEmpty()) {
            analysisResultRepository.saveAll(results);
            log.info("DB 배치 저장 완료: {} 개", results.size());
        }

        // 4단계: 결과 출력
        generateReport(results);

        return results;
    }

    private String removeJsonBlock(String fullText) {
        int start = fullText.indexOf("```json");
        if (start == -1) {
            return fullText;
        }

        int end = fullText.indexOf("```", start + 6);
        if (end == -1) {
            return fullText;
        }

        // json 코드블록 전체 제거
        return (fullText.substring(0, start) + fullText.substring(end + 3)).trim();
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
