package com.stock.stockserver.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.application.StockAnalysisService;
import com.stock.stockserver.domain.AnalysisJob;
import com.stock.stockserver.domain.AnalysisStatus;
import com.stock.stockserver.dto.AnalysisResultDto;
import com.stock.stockserver.dto.AnalysisStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock
    private StockAnalysisService analysisService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private AnalysisController analysisController;

    @BeforeEach
    void setUp() {
        analysisController = new AnalysisController(analysisService, kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("runAnalysis - 분석 요청 시 200 응답")
    void runAnalysis_success() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"analysisId\":\"test-id\"}");
        doReturn(null).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        ResponseEntity<?> response = analysisController.runAnalysis();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("getStatus - 분석 상태 조회")
    void getStatus_success() {
        String analysisId = "test-id";
        AnalysisJob job = mock(AnalysisJob.class);
        when(job.getStatus()).thenReturn(AnalysisStatus.RUNNING);
        when(analysisService.getAnalysisJob(analysisId)).thenReturn(job);

        ResponseEntity<AnalysisStatusDto> response = analysisController.getStatus(analysisId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(AnalysisStatus.RUNNING, response.getBody().analysisStatus());
    }

    @Test
    @DisplayName("getStatus - 분석 작업이 없는 경우 404 응답")
    void getStatus_notFound() {
        String analysisId = "non-existent-id";
        when(analysisService.getAnalysisJob(analysisId)).thenReturn(null);

        ResponseEntity<AnalysisStatusDto> response = analysisController.getStatus(analysisId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("getResult - 분석 결과 조회")
    void getResult_success() {
        String analysisId = "test-id";
        AnalysisJob job = mock(AnalysisJob.class);
        when(job.getStatus()).thenReturn(AnalysisStatus.DONE);
        when(analysisService.getAnalysisJob(analysisId)).thenReturn(job);

        ResponseEntity<List<AnalysisResultDto>> response = analysisController.getResult(analysisId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("getResult - 분석이 아직 완료되지 않은 경우 202 응답")
    void getResult_notDone() {
        String analysisId = "test-id";
        AnalysisJob job = mock(AnalysisJob.class);
        when(job.getStatus()).thenReturn(AnalysisStatus.RUNNING);
        when(analysisService.getAnalysisJob(analysisId)).thenReturn(job);

        ResponseEntity<List<AnalysisResultDto>> response = analysisController.getResult(analysisId);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    @DisplayName("getResult - 분석 작업이 없는 경우 404 응답")
    void getResult_notFound() {
        String analysisId = "non-existent-id";
        when(analysisService.getAnalysisJob(analysisId)).thenReturn(null);

        ResponseEntity<List<AnalysisResultDto>> response = analysisController.getResult(analysisId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("getLatestAnalysis - 최근 분석 결과 조회")
    void getLatestAnalysis_success() {
        AnalysisResultDto dto = new AnalysisResultDto(
                "005930", 
                "Samsung", 
                null, 
                "Analysis text", 
                com.stock.stockserver.domain.RecommendationStatus.BUY
        );
        List<AnalysisResultDto> mockResults = Arrays.asList(dto);
        when(analysisService.getLatestAnalysis()).thenReturn(mockResults);

        ResponseEntity<List<AnalysisResultDto>> response = analysisController.getLatestAnalysis();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
