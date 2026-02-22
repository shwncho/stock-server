package com.stock.stockserver.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.dto.AnalysisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisConsumerTest {

    @Mock
    private StockAnalysisService analysisService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private AnalysisConsumer analysisConsumer;

    @BeforeEach
    void setUp() {
        analysisConsumer = new AnalysisConsumer(analysisService, objectMapper);
    }

    @Test
    @DisplayName("consumeAnalysisRequest - 정상 메시지 처리")
    void consumeAnalysisRequest_success() throws Exception {
        String message = "{\"analysisId\":\"test-id\",\"requestedAt\":\"2024-01-01T10:00:00\"}";
        AnalysisEvent event = new AnalysisEvent("test-id", LocalDateTime.now());
        
        when(objectMapper.readValue(message, AnalysisEvent.class)).thenReturn(event);

        analysisConsumer.consumeAnalysisRequest(message, acknowledgment);

        verify(analysisService, times(1)).saveJob("test-id");
        verify(analysisService, times(1)).runFullAnalysis("test-id");
        verify(acknowledgment, times(1)).acknowledge();
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
}
