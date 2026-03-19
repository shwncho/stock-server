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
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component("gptStrategy")
@RequiredArgsConstructor
@Slf4j
public class GPTAnalysisStrategy implements LLMAnalysisStrategy {

    @Value("${llm.gpt.base-url}")
    private String baseUrl;

    @Value("${llm.gpt.api-key}")
    private String apiKey;

    @Value("${llm.gpt.model}")
    private String model;

    @Value("${llm.gpt.max-tokens}")
    private int maxTokens;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final LLMAnalysisPromptBuilder promptBuilder;

    @Override
    public String getProviderName() {
        return "gpt";
    }

    @Override
    public String analyze(StockDataDto stockData) {
        String prompt = buildPrompt(stockData);

        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            String responseBody = webClient.post()
                    .uri(baseUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(10))
                            .doBeforeRetry(retrySignal ->
                                    log.warn("GPT API 재시도: {} - 시도 {}/{}",
                                            stockData.stockCode(),
                                            retrySignal.totalRetries() + 1, 2)))
                    .block();

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String analysis = (String) message.get("content");
                log.info("GPT 분석 완료: {}", stockData.stockCode());
                return analysis;
            }
            return null;

        } catch (Exception e) {
            log.error("GPT API 호출 실패: {}", stockData.stockCode(), e);
            return null;
        }
    }

    @Override
    public String buildPrompt(StockDataDto stockData) {
        return promptBuilder.build(stockData);
    }
}
