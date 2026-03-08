package com.stock.stockserver.infrastructure.strategy;

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

    @Value("${llm.claude.api-key}")
    private String apiKey;

    @Value("${llm.claude.model}")
    private String model;

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

        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", 2000,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            String responseBody = webClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");

            if (content != null && !content.isEmpty()) {
                String analysis = (String) content.get(0).get("text");
                log.info("Claude 분석 완료: {}", stockData.stockCode());
                return analysis;
            }
            return null;

        } catch (Exception e) {
            log.error("Claude API 호출 실패: {}", stockData.stockCode(), e);
            return null;
        }
    }

    @Override
    public String buildPrompt(StockDataDto stockData) {
        return promptBuilder.build(stockData);
    }
}
