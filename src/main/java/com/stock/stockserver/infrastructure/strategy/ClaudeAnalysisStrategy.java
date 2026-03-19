package com.stock.stockserver.infrastructure.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.dto.StockDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component("claudeStrategy")
@RequiredArgsConstructor
@Slf4j
public class ClaudeAnalysisStrategy implements LLMAnalysisStrategy {

    @Value("${llm.claude.base-url}")
    private String baseUrl;

    @Value("${llm.claude.api-key}")
    private String apiKey;

    @Value("${llm.claude.model}")
    private String model;

    @Value("${llm.claude.max-tokens}")
    private int maxTokens;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final LLMAnalysisPromptBuilder promptBuilder;

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public String analyze(StockDataDto stockData) {
        String prompt = buildPrompt(stockData);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            String responseBody = webClient.post()
                    .uri(baseUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(responseBody, stockData);

        } catch (Exception e) {
            log.error("Claude API 호출 실패: {}", stockData.stockCode(), e);
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    private String parseResponse(String responseBody, StockDataDto stockData) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        
        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new IllegalStateException("Claude response에 content가 없습니다.");
        }

        for (JsonNode item : content) {
            String type = item.has("type") ? item.get("type").asText() : "";
            if ("text".equals(type)) {
                String text = item.has("text") ? item.get("text").asText() : "";
                log.info("Claude 분석 완료: {}", stockData.stockCode());
                return text;
            }
        }

        throw new IllegalStateException("Claude response에서 text content를 찾을 수 없습니다.");
    }

    @Override
    public String buildPrompt(StockDataDto stockData) {
        return promptBuilder.build(stockData);
    }
}
