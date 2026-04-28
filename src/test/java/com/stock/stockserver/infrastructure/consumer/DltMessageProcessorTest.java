package com.stock.stockserver.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.domain.entity.FailedAnalysisRequest;
import com.stock.stockserver.domain.repository.FailedAnalysisRequestRepository;
import com.stock.stockserver.dto.AnalysisEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DltMessageProcessorTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FailedAnalysisRequestRepository failedAnalysisRequestRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private DltMessageProcessor dltMessageProcessor;

    @Test
    @DisplayName("processDltMessage - 정상적인 메시지 처리")
    void processDltMessage_success() throws Exception {
        String message = "{\"analysisId\":\"test-id\",\"requestedAt\":\"2024-01-01T10:00:00\"}";
        AnalysisEvent event = new AnalysisEvent("test-id", AnalysisTarget.ALL, LocalDateTime.now());
        
        when(objectMapper.readValue(message, AnalysisEvent.class)).thenReturn(event);
        when(failedAnalysisRequestRepository.save(any(FailedAnalysisRequest.class)))
                .thenReturn(null);

        dltMessageProcessor.processDltMessage(message, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        
        ArgumentCaptor<FailedAnalysisRequest> captor = ArgumentCaptor.forClass(FailedAnalysisRequest.class);
        verify(failedAnalysisRequestRepository).save(captor.capture());
        
        FailedAnalysisRequest saved = captor.getValue();
        assertEquals("test-id", saved.getAnalysisId());
        assertFalse(saved.getProcessed());
    }

    @Test
    @DisplayName("processDltMessage - 파싱 실패 시 DB에 저장")
    void processDltMessage_parseFailure() throws Exception {
        String message = "invalid-message";
        
        when(objectMapper.readValue(anyString(), eq(AnalysisEvent.class)))
                .thenThrow(new RuntimeException("Parse error"));
        when(failedAnalysisRequestRepository.save(any(FailedAnalysisRequest.class)))
                .thenReturn(null);

        dltMessageProcessor.processDltMessage(message, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        
        ArgumentCaptor<FailedAnalysisRequest> captor = ArgumentCaptor.forClass(FailedAnalysisRequest.class);
        verify(failedAnalysisRequestRepository).save(captor.capture());
        
        FailedAnalysisRequest saved = captor.getValue();
        assertEquals("unknown", saved.getAnalysisId());
        assertEquals("invalid-message", saved.getOriginalMessage());
    }
}
