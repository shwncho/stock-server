package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LLMApiClientTest {

    @Mock
    private Map<String, LLMAnalysisStrategy> strategies;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LLMAnalysisStrategy mockGptStrategy;

    @Mock
    private LLMAnalysisStrategy mockClaudeStrategy;

    @InjectMocks
    private LLMApiClient llmApiClient;

    private StockDataDto testStockData;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(llmApiClient, "provider", "gpt");

        testStockData = new StockDataDto(
                AnalysisTarget.DOMESTIC,
                "KRX",
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

        when(mockGptStrategy.getProviderName()).thenReturn("gpt");
        when(mockClaudeStrategy.getProviderName()).thenReturn("claude");
    }

    @Test
    @DisplayName("analyzeStock - 성공적인 분석")
    void analyzeStock_success() throws Exception {
        when(strategies.get("gptStrategy")).thenReturn(mockGptStrategy);
        when(strategies.get("claudeStrategy")).thenReturn(mockClaudeStrategy);
        
        when(mockGptStrategy.analyze(any(StockDataDto.class))).thenReturn(
                "Some analysis text. {\"recommendation\":\"BUY\",\"confidence\":0.85,\"summary\":\"Good stock\"}"
        );

        JsonNode recommendationNode = mock(JsonNode.class);
        when(recommendationNode.asText("")).thenReturn("BUY");
        
        JsonNode confidenceNode = mock(JsonNode.class);
        when(confidenceNode.asDouble(0.0)).thenReturn(0.85);
        
        JsonNode summaryNode = mock(JsonNode.class);
        when(summaryNode.asText("")).thenReturn("Good stock");
        
        JsonNode mockJsonNode = mock(JsonNode.class);
        when(mockJsonNode.path("recommendation")).thenReturn(recommendationNode);
        when(mockJsonNode.path("confidence")).thenReturn(confidenceNode);
        when(mockJsonNode.path("summary")).thenReturn(summaryNode);
        
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
    @DisplayName("analyzeStock - GPT 실패 시 Claude로 fallback")
    void analyzeStock_gptFallbackToClaude() throws Exception {
        when(strategies.get("gptStrategy")).thenReturn(mockGptStrategy);
        when(strategies.get("claudeStrategy")).thenReturn(mockClaudeStrategy);
        
        when(mockGptStrategy.analyze(any(StockDataDto.class))).thenThrow(new RuntimeException("GPT Error"));
        when(mockClaudeStrategy.analyze(any(StockDataDto.class))).thenReturn(
                "Claude analysis. {\"recommendation\":\"HOLD\",\"confidence\":0.6,\"summary\":\"Hold it\"}"
        );

        JsonNode recommendationNode = mock(JsonNode.class);
        when(recommendationNode.asText("")).thenReturn("HOLD");
        
        JsonNode confidenceNode = mock(JsonNode.class);
        when(confidenceNode.asDouble(0.0)).thenReturn(0.6);
        
        JsonNode summaryNode = mock(JsonNode.class);
        when(summaryNode.asText("")).thenReturn("Hold it");
        
        JsonNode mockJsonNode = mock(JsonNode.class);
        when(mockJsonNode.path("recommendation")).thenReturn(recommendationNode);
        when(mockJsonNode.path("confidence")).thenReturn(confidenceNode);
        when(mockJsonNode.path("summary")).thenReturn(summaryNode);
        
        when(objectMapper.readTree(anyString())).thenReturn(mockJsonNode);

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.HOLD, result.recommendation());
    }

    @Test
    @DisplayName("analyzeStock - 모든 LLM 실패 시 ERROR 반환")
    void analyzeStock_allFailed() throws Exception {
        when(strategies.get("gptStrategy")).thenReturn(mockGptStrategy);
        when(strategies.get("claudeStrategy")).thenReturn(mockClaudeStrategy);
        
        when(mockGptStrategy.analyze(any(StockDataDto.class))).thenThrow(new RuntimeException("GPT Error"));
        when(mockClaudeStrategy.analyze(any(StockDataDto.class))).thenThrow(new RuntimeException("Claude Error"));

        LLMAnalysisResponseDto result = llmApiClient.analyzeStock(testStockData);

        assertNotNull(result);
        assertEquals(RecommendationStatus.ERROR, result.recommendation());
        assertTrue(result.fullAnalysis().contains("GPT Error"));
        assertTrue(result.fullAnalysis().contains("Claude Error"));
    }
}
