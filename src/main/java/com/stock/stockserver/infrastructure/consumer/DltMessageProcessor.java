package com.stock.stockserver.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.entity.FailedAnalysisRequest;
import com.stock.stockserver.domain.repository.FailedAnalysisRequestRepository;
import com.stock.stockserver.dto.AnalysisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DltMessageProcessor {

    private final ObjectMapper objectMapper;
    private final FailedAnalysisRequestRepository failedAnalysisRequestRepository;

    @KafkaListener(
            topics = "analysis-requests.DLT",
            groupId = "${spring.kafka.consumer.group-id:stock-analysis-group}-dlt",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processDltMessage(String message, Acknowledgment ack) {
        log.warn("DLT에서 메시지 수신: message={}", message);
        
        try {
            AnalysisEvent event = objectMapper.readValue(message, AnalysisEvent.class);
            
            handleDltEvent(event);
            
            log.info("DLT 메시지 처리 완료: analysisId={}", event.analysisId());
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("DLT 메시지 파싱 실패, DB에 저장: message={}", message, e);
            saveFailedMessage(message, e.getMessage());
            ack.acknowledge();
        }
    }

    private void handleDltEvent(AnalysisEvent event) {
        log.warn("분석 실패로 인한 DLT 라우팅: analysisId={}, target={}, requestedAt={}",
                event.analysisId(), event.resolvedTarget(), event.requestedAt());
        
        FailedAnalysisRequest failedRequest = FailedAnalysisRequest.builder()
                .analysisId(event.analysisId())
                .originalMessage(String.format(
                        "{\"analysisId\":\"%s\",\"target\":\"%s\",\"requestedAt\":\"%s\"}",
                        event.analysisId(), event.resolvedTarget(), event.requestedAt()))
                .errorMessage("Consumer 처리 실패 - 최대 재시도 횟수 초과")
                .failedAt(LocalDateTime.now())
                .processed(false)
                .build();
        
        failedAnalysisRequestRepository.save(failedRequest);
    }

    private void saveFailedMessage(String message, String errorMessage) {
        log.error("DLT 메시지 저장 (파싱 실패): message={}, error={}", message, errorMessage);
        
        FailedAnalysisRequest failedRequest = FailedAnalysisRequest.builder()
                .analysisId("unknown")
                .originalMessage(message)
                .errorMessage(errorMessage)
                .failedAt(LocalDateTime.now())
                .processed(false)
                .build();
        
        failedAnalysisRequestRepository.save(failedRequest);
    }
}
