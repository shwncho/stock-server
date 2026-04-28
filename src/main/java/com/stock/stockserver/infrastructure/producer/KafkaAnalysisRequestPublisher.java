package com.stock.stockserver.infrastructure.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.AnalysisRequestPublisher;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.dto.AnalysisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAnalysisRequestPublisher implements AnalysisRequestPublisher {

    private static final String ANALYSIS_TOPIC = "analysis-requests";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String analysisId) {
        publish(analysisId, AnalysisTarget.ALL);
    }

    @Override
    public void publish(String analysisId, AnalysisTarget target) {
        String message = toMessage(analysisId, target);
        kafkaTemplate.send(ANALYSIS_TOPIC, analysisId, message);
        log.info("Analysis request sent to Kafka: analysisId={}, target={}", analysisId, target);
    }

    @Override
    public void publishAndWaitForAck(String analysisId) throws Exception {
        publishAndWaitForAck(analysisId, AnalysisTarget.ALL);
    }

    @Override
    public void publishAndWaitForAck(String analysisId, AnalysisTarget target) throws Exception {
        String message = toMessage(analysisId, target);
        kafkaTemplate.send(ANALYSIS_TOPIC, analysisId, message).get();
        log.info("Analysis request re-sent to Kafka: analysisId={}, target={}", analysisId, target);
    }

    private String toMessage(String analysisId, AnalysisTarget target) {
        try {
            return objectMapper.writeValueAsString(AnalysisEvent.of(analysisId, target));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("분석 요청 메시지 직렬화에 실패했습니다. analysisId=" + analysisId, e);
        }
    }
}
