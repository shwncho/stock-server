package com.stock.stockserver.presentation;

import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.domain.entity.LLMAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final StockAnalysisService analysisService;

    /**
     * 분석 실행
     */
    @PostMapping("/run")
    public ResponseEntity<List<LLMAnalysisResult>> runAnalysis() {
        log.info("분석 실행 요청");
        List<LLMAnalysisResult> results = analysisService.runFullAnalysis();
        return ResponseEntity.ok(results);
    }

    /**
     * 최근 분석 결과 조회
     */
    @GetMapping("/latest")
    public ResponseEntity<List<LLMAnalysisResult>> getLatestAnalysis() {
        List<LLMAnalysisResult> results = analysisService.getLatestAnalysis();
        return ResponseEntity.ok(results);
    }
}
