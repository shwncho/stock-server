package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.RecommendationStatus;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.stock.stockserver.infrastructure.strategy.LLMAnalysisStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LLMApiClient {

    @Value("${llm.provider}")
    private String provider;

    private final Map<String, LLMAnalysisStrategy> strategies;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = "llmAnalysisCache",
            key = "#stockData.stockCode() + '_' + T(java.time.LocalDateTime).now().getHour()",
            unless = "#result == null"
    )
    public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) {
        LLMAnalysisStrategy primaryStrategy = strategies.get(provider.toLowerCase() + "Strategy");

        if (primaryStrategy == null) {
            log.error("알 수 없는 LLM provider: {}", provider);
            return createErrorResponse("Unknown provider: " + provider);
        }

        LLMAnalysisStrategy fallbackStrategy = getFallbackStrategy(primaryStrategy);

        // 1차: Primary LLM 시도
        try {
            return executeAnalysis(primaryStrategy, stockData);
        } catch (Exception e) {
            log.warn("Primary LLM 실패, fallback 시도: {} - {}", stockData.stockCode(), e.getMessage());

            // 2차: Fallback LLM 시도
            if (fallbackStrategy != null) {
                try {
                    return executeAnalysis(fallbackStrategy, stockData);
                } catch (Exception fallbackException) {
                    log.error("Fallback LLM도 실패: {} - {}", stockData.stockCode(), fallbackException.getMessage());
                    return createErrorResponse(
                            "Primary failed: " + e.getMessage() + ", Fallback failed: " + fallbackException.getMessage()
                    );
                }
            }

            log.error("모든 LLM 분석 실패: {}", stockData.stockCode(), e);
            return createErrorResponse(e.getMessage());
        }
    }

    private LLMAnalysisResponseDto executeAnalysis(LLMAnalysisStrategy strategy, StockDataDto stockData) {
        String analysisText = strategy.analyze(stockData);

        if (analysisText == null) {
            throw new IllegalStateException("Analysis returned null");
        }

        return parseLLMResponse(analysisText);
    }

    private LLMAnalysisStrategy getFallbackStrategy(LLMAnalysisStrategy primaryStrategy) {
        String primaryName = primaryStrategy.getProviderName().toLowerCase();

        if ("gpt".equals(primaryName)) {
            return strategies.get("claudeStrategy");
        } else if ("claude".equals(primaryName)) {
            return strategies.get("gptStrategy");
        }

        return null;
    }

    private LLMAnalysisResponseDto createErrorResponse(String message) {
        return LLMAnalysisResponseDto.builder()
                .recommendation(RecommendationStatus.ERROR)
                .confidence(0.0)
                .summary("분석 처리 중 오류가 발생했습니다.")
                .fullAnalysis(message)
                .build();
    }

    private LLMAnalysisResponseDto parseLLMResponse(String fullText) {
        try {
            int jsonStart = fullText.lastIndexOf("{");
            if (jsonStart == -1) {
                throw new IllegalStateException("JSON 시작점 없음");
            }

            String jsonPart = fullText.substring(jsonStart).trim();
            String analysisPart = fullText.substring(0, jsonStart).trim();

            JsonNode root = objectMapper.readTree(jsonPart);

            String recommendationStr = root.get("recommendation").asText();
            double confidence = root.get("confidence").asDouble();
            String summary = root.get("summary").asText();

            return LLMAnalysisResponseDto.builder()
                    .recommendation(
                            Enum.valueOf(RecommendationStatus.class, recommendationStr)
                    )
                    .confidence(confidence)
                    .summary(summary)
                    .fullAnalysis(analysisPart)
                    .build();

        } catch (Exception e) {
            log.error("LLM 응답 파싱 실패", e);

            return LLMAnalysisResponseDto.builder()
                    .recommendation(RecommendationStatus.ERROR)
                    .confidence(0.0)
                    .summary("분석 결과 파싱에 실패했습니다.")
                    .fullAnalysis(fullText)
                    .build();
        }
    }
}
