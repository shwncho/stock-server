package com.stock.stockserver.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.dto.AnalysisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(MockitoExtension.class)
class AnalysisConsumerTest {

    @Mock
    private StockAnalysisService analysisService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private AnalysisConsumer analysisConsumer;

    @Test
    @DisplayName("consumeAnalysisRequest - 정상 메시지 처리")
    void consumeAnalysisRequest_success() throws Exception {
        String message = "{\"analysisId\":\"test-id\",\"requestedAt\":\"2024-01-01T10:00:00\"}";
        AnalysisEvent event = new AnalysisEvent("test-id", AnalysisTarget.ALL, LocalDateTime.now());
        
        when(objectMapper.readValue(message, AnalysisEvent.class)).thenReturn(event);

        analysisConsumer.consumeAnalysisRequest(message, acknowledgment);

        verify(analysisService, times(1)).saveJob("test-id");
        verify(analysisService, times(1)).runFullAnalysis("test-id", AnalysisTarget.ALL);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @ParameterizedTest
    @MethodSource("analysisTargets")
    @DisplayName("consumeAnalysisRequest - 분석 대상별 메시지 처리")
    void consumeAnalysisRequest_byTarget(AnalysisTarget target) throws Exception {
        String message = "message-" + target;
        AnalysisEvent event = new AnalysisEvent("test-id", target, LocalDateTime.now());

        when(objectMapper.readValue(message, AnalysisEvent.class)).thenReturn(event);

        analysisConsumer.consumeAnalysisRequest(message, acknowledgment);

        verify(analysisService).saveJob("test-id");
        verify(analysisService).runFullAnalysis("test-id", target);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consumeAnalysisRequest - 예외 발생 시RuntimeException 발생")
    void consumeAnalysisRequest_exception() throws Exception {
        String message = "invalid-message";
        when(objectMapper.readValue(anyString(), eq(AnalysisEvent.class)))
                .thenThrow(new RuntimeException("Parse error"));

        try {
            analysisConsumer.consumeAnalysisRequest(message, acknowledgment);
        } catch (RuntimeException e) {
            // 예상된 예외
        }

        verify(analysisService, never()).runFullAnalysis(anyString());
    }

    private static Stream<AnalysisTarget> analysisTargets() {
        return Stream.of(AnalysisTarget.DOMESTIC, AnalysisTarget.OVERSEAS, AnalysisTarget.ALL);
    }
}
