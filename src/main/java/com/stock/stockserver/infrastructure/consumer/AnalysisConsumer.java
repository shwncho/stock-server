package com.stock.stockserver.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.dto.AnalysisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisConsumer {

    private final StockAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "analysis-requests",
            groupId = "${spring.kafka.consumer.group-id:stock-analysis-group}",
            concurrency = "3"
    ) public void consumeAnalysisRequest(String message, Acknowledgment ack) {
        try {
            AnalysisEvent event = objectMapper.readValue(message, AnalysisEvent.class);
            String analysisId = event.analysisId();

            log.info("Received analysis request from Kafka: analysisId={}", analysisId);

            analysisService.saveJob(analysisId);
            analysisService.runFullAnalysis(analysisId);

            ack.acknowledge();

            log.info("Analysis completed: analysisId={}", analysisId);
        } catch (Exception e) {
            log.error("Failed to process analysis request: message={}", message, e);
            throw new RuntimeException("Analysis processing failed", e);
        }
    }
}
