package com.stock.stockserver.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.domain.AnalysisJob;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.dto.AnalysisEvent;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.AnalysisStatusDto;
import com.stock.stockserver.dto.PostAnalysisDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final StockAnalysisService analysisService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String ANALYSIS_TOPIC = "analysis-requests";

    /**
     * 분석 실행
     */
    @PostMapping("/run")
    public ResponseEntity<PostAnalysisDto> runAnalysis() throws JsonProcessingException {
        String analysisId = UUID.randomUUID().toString();

        AnalysisEvent event = AnalysisEvent.of(analysisId);
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(ANALYSIS_TOPIC, analysisId, message);

        log.info("Analysis request sent to Kafka: analysisId={}", analysisId);

        return ResponseEntity.ok(
                new PostAnalysisDto(
                    analysisId,
                    AnalysisStatus.RUNNING
                )
        );

    }

    @GetMapping("/status/{analysisId}")
    public ResponseEntity<AnalysisStatusDto> getStatus(@PathVariable String analysisId) {
        AnalysisJob job = analysisService.getAnalysisJob(analysisId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                new AnalysisStatusDto(
                        job.getStatus()
                )
        );
    }

    @GetMapping("/result/{analysisId}")
    public ResponseEntity<List<AnalysisResultDto>> getResult(@PathVariable String analysisId) {
        AnalysisJob job = analysisService.getAnalysisJob(analysisId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != AnalysisStatus.DONE) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        return ResponseEntity.ok(
                job.getResults().stream()
                        .map(AnalysisResultDto::from)
                        .toList()
        );
    }

    /**
     * 최근 분석 결과 조회
     */
    @GetMapping("/latest")
    public ResponseEntity<List<AnalysisResultDto>> getLatestAnalysis() {
        List<AnalysisResultDto> results = analysisService.getLatestAnalysis();
        return ResponseEntity.ok(results);
    }
}
