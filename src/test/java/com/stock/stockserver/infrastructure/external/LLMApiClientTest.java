package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.RecommendationStatus;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.strategy.LLMAnalysisStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LLMApiClientTest {

    @Mock
    private Map<String, LLMAnalysisStrategy> strategies;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LLMAnalysisStrategy mockStrategy;

    @InjectMocks
    private LLMApiClient llmApiClient;

    private StockDataDto testStockData;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(llmApiClient, "provider", "openai");

        testStockData = new StockDataDto(
                "005930",
                "Samsung",
                new BigDecimal("75000"),
                new BigDecimal("2.5"),
                1000000L,
                75000000000L,
                new BigDecimal("80000"),
                new BigDecimal("50000"),
                LocalDate.now(),
                "[]"
        );
    }

    @Test
    @DisplayName("analyzeStock - 성공적인 분석")
    void analyzeStock_success() throws Exception {
        when(strategies.get("openaiStrategy")).thenReturn(mockStrategy);
        when(mockStrategy.analyze(any(StockDataDto.class))).thenReturn(
                "Some analysis text. {\"recommendation\":\"BUY\",\"confidence\":0.85,\"summary\":\"Good stock\"}"
        );

        JsonNode recommendationNode = mock(JsonNode.class);
        when(recommendationNode.asText()).thenReturn("BUY");
        
        JsonNode confidenceNode = mock(JsonNode.class);
        when(confidenceNode.asDouble()).thenReturn(0.85);
        
        JsonNode summaryNode = mock(JsonNode.class);
        when(summaryNode.asText()).thenReturn("Good stock");
        
        JsonNode mockJsonNode = mock(JsonNode.class);
        when(mockJsonNode.get("recommendation")).thenReturn(recommendationNode);
        when(mockJsonNode.get("confidence")).thenReturn(confidenceNode);
        when(mockJsonNode.get("summary")).thenReturn(summaryNode);
        
        when(objectMapper.readTree(anyString())).thenReturn(mockJsonNode);

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.BUY, result.recommendation());
        assertEquals(0.85, result.confidence());
        assertEquals("Good stock", result.summary());
    }

    @Test
    @DisplayName("analyzeStock - 알 수 없는 provider")
    void analyzeStock_unknownProvider() {
        ReflectionTestUtils.setField(llmApiClient, "provider", "unknown");

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.ERROR, result.recommendation());
        assertTrue(result.summary().contains("오류"));
    }

    @Test
    @DisplayName("analyzeStock - strategy가 null을 반환하는 경우")
    void analyzeStock_nullAnalysis() throws Exception {
        when(strategies.get("openaiStrategy")).thenReturn(mockStrategy);
        when(mockStrategy.analyze(any(StockDataDto.class))).thenReturn(null);

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.ERROR, result.recommendation());
    }

    @Test
    @DisplayName("analyzeStock - strategy에서 예외 발생")
    void analyzeStock_exception() throws Exception {
        when(strategies.get("openaiStrategy")).thenReturn(mockStrategy);
        when(mockStrategy.analyze(any(StockDataDto.class))).thenThrow(new RuntimeException("API Error"));

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.ERROR, result.recommendation());
    }

    @Test
    @DisplayName("analyzeStock - 잘못된 JSON 응답 파싱")
    void analyzeStock_invalidJson() throws Exception {
        when(strategies.get("openaiStrategy")).thenReturn(mockStrategy);
        when(mockStrategy.analyze(any(StockDataDto.class))).thenReturn("Invalid response");

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.ERROR, result.recommendation());
    }
}
