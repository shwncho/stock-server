package com.stock.stockserver.application;

import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.domain.entity.AnalysisJob;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import com.stock.stockserver.domain.repository.AnalysisJobStore;
import com.stock.stockserver.domain.repository.LLMAnalysisResultRepository;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.external.LLMApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockDataCollectionService dataCollectionService;
    private final LLMApiClient llmApiClient;
    private final LLMAnalysisResultRepository analysisResultRepository;
    private final AnalysisResultSaveService analysisResultSaveService;
    private final AnalysisJobStore jobStore;
    private final Executor llmApiExecutor;

    public void runFullAnalysis(String analysisId) {
        try {
            List<LLMAnalysisResult> results = runFullAnalysisInternal(analysisId);
            jobStore.save(AnalysisJob.builder()
                    .analysisId(analysisId)
                    .status(AnalysisStatus.DONE)
                    .errorMessage(null)
                    .build());
        } catch (Exception e) {
            log.error("분석 실패: analysisId={}, error={}", analysisId, e.getMessage(), e);
            jobStore.save(AnalysisJob.builder()
                    .analysisId(analysisId)
                    .status(AnalysisStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
            throw new RuntimeException("분석 실패: " + analysisId, e);
        }
    }

    public List<LLMAnalysisResult> runFullAnalysisInternal(String analysisId) {
        log.info("\n");
        log.info("=====================================");
        log.info("  KIS Stock Analysis with LLM (병렬)");
        log.info("=====================================\n");

        List<StockDataDto> stockDataList = dataCollectionService.collectStockData();

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
                                    .analysisId(analysisId)
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

        List<LLMAnalysisResult> results = futures.stream()
                .map(f -> f.orTimeout(60, TimeUnit.SECONDS))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!results.isEmpty()) {
            analysisResultSaveService.saveAll(results);
        }

        generateReport(results);

        return results;
    }

    private String removeJsonBlock(String fullText) {
        if (fullText == null) return null;

        int start = fullText.indexOf("```json");
        if (start == -1) {
            return fullText;
        }

        int end = fullText.indexOf("```", start + 6);
        if (end == -1) {
            return fullText;
        }

        return (fullText.substring(0, start) + fullText.substring(end + 3)).trim();
    }

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

    public List<AnalysisResultDto> getLatestAnalysis() {
        List<LLMAnalysisResult> results = analysisResultRepository.findTop10ByAnalysisDateOrderByCreatedAtDesc(LocalDate.now());
        return results.stream()
                .map(AnalysisResultDto::from)
                .toList();
    }

    public List<AnalysisResultDto> getAnalysisResults(String analysisId) {
        List<LLMAnalysisResult> results = analysisResultRepository.findByAnalysisIdOrderByCreatedAtDesc(analysisId);
        return results.stream()
                .map(AnalysisResultDto::from)
                .toList();
    }

    public AnalysisStatus getJobStatus(String analysisId) {
        return jobStore.getStatus(analysisId);
    }

    public AnalysisJob getAnalysisJob(String analysisId) {
        return jobStore.get(analysisId);
    }

    public void saveJob(String analysisId) {
        jobStore.save(AnalysisJob.builder()
                .analysisId(analysisId)
                .status(AnalysisStatus.RUNNING)
                .errorMessage(null)
                .build());
    }
}