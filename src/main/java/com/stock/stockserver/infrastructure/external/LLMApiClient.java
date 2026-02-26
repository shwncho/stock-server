package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.stock.stockserver.domain.RecommendationStatus;
import com.stock.stockserver.dto.LLMAnalysisResponseDto;
import com.stock.stockserver.dto.StockDataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class LLMApiClient {

    @Value("${llm.provider}")
    private String provider;

    @Value("${llm.claude.api-key}")
    private String claudeApiKey;

    @Value("${llm.claude.model}")
    private String claudeModel;

    @Value("${llm.gpt.api-key}")
    private String gptApiKey;

    @Value("${llm.gpt.model}")
    private String gptModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * LLM에 주식 분석 요청
     */
    @Cacheable(
            cacheNames = "llmAnalysisCache",
            key = "#stockData.stockCode()",
            unless = "#result == null"
    )
    public LLMAnalysisResponseDto analyzeStock(StockDataDto stockData) {

        String analysisText;

        if ("claude".equalsIgnoreCase(provider)) {
            analysisText = analyzeWithClaude(stockData);
        } else if ("gpt".equalsIgnoreCase(provider)) {
            analysisText = analyzeWithGPT(stockData);
        } else {
            log.error("알 수 없는 LLM provider: {}", provider);
            return null;
        }

        if (analysisText == null) {
            return null;
        }

        return parseLLMResponse(analysisText);
    }

    /**
     * Claude API를 이용한 분석
     */
    private String analyzeWithClaude(StockDataDto stockData) {
        String prompt = buildAnalysisPrompt(stockData);

        Map<String, Object> request = new HashMap<>();
        request.put("model", claudeModel);
        request.put("max_tokens", 2000);
        request.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        try {
            String responseBody = webClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + claudeApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            // Claude 응답 파싱
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

    /**
     * GPT API를 이용한 분석
     */
    private String analyzeWithGPT(StockDataDto stockData) {
        String prompt = buildAnalysisPrompt(stockData);

        Map<String, Object> request = new HashMap<>();
        request.put("model", gptModel);
        request.put("max_tokens", 2000);
        request.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        try {
            String responseBody = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + gptApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45)) // GPT API 타임아웃 45초
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)) // 실패 시 재시도
                            .maxBackoff(Duration.ofSeconds(10))
                            .doBeforeRetry(retrySignal -> 
                                log.warn("GPT API 재시도: {} - 시도 {}/{}", 
                                    stockData.stockCode(), 
                                    retrySignal.totalRetries() + 1, 2)))
                    .block();

            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            // GPT 응답 파싱
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

    /**
     * 분석 프롬프트 생성
     */
    private String buildAnalysisPrompt(StockDataDto stockData) {
        return String.format(
                """
                다음은 한국 주식 시장의 거래량 상위 종목 중 하나의 데이터입니다.
                제공된 가격 및 거래량 데이터를 기반으로 기술적 분석을 수행하고 투자 의견을 제시해주세요.
    
                [종목 정보]
                종목명: %s
                종목코드: %s
    
                [현재 시세 정보]
                현재가: %,.0f원
                변동률: %+.2f%%
                거래량: %,d주
                거래대금: %,d원
    
                [52주 가격 범위]
                52주 최고가: %,.0f원
                52주 최저가: %,.0f원
                현재 가격 수준: %.2f%% (52주 최저가 대비)
    
                [최근 60일 일봉 데이터]
                %s
    
                -----------------------
                [분석 요청사항]
    
                1. 위 데이터를 기반으로 기술적 분석 (추세, 지지선/저항선, 이동평균 관점 등)
                2. 거래량 분석 (최근 거래량 패턴 및 의미)
                3. 가격 변동 패턴 분석 (변동성 및 주요 흐름)
                4. 단기(1년 이내), 중기(3~5년), 장기(5~10년) 관점을 고려하되,
                   최종 투자 의견은 반드시 하나의 종합 판단(매수/매도)으로 제시하세요.
                5. 투자 근거 및 주의사항 제시
    
                -----------------------
                [출력 형식 규칙]
    
                - 분석 본문은 반드시 Markdown 문법으로 작성하세요.
                - 모든 섹션 헤더는 반드시 정확히 '### ' 로 시작해야 합니다.
                - #### 또는 다른 단계의 헤더는 사용하지 마세요.
                - 리스트는 반드시 '-' 로 작성하세요.
                - recommendation 단어는 JSON 외의 영역에서 절대 사용하지 마세요.
    
                -----------------------
                [JSON 출력 규칙]
    
                - 반드시 응답의 가장 마지막 줄에 순수 JSON 객체만 출력하세요.
                - JSON은 코드블럭(```)을 절대 사용하지 마세요.
                - JSON 객체 이후에는 어떠한 텍스트도 출력하지 마세요.
                - recommendation 값은 BUY, SELL 중 하나만 가능합니다.
                - confidence는 0.0 이상 1.0 이하의 소수점 숫자로 작성하세요.
                - summary는 한 줄 요약입니다.
    
                {
                  "recommendation": "BUY|SELL",
                  "confidence": 0.0,
                  "summary": "한 줄 요약"
                }
                """,
                stockData.stockName(),
                stockData.stockCode(),
                stockData.currentPrice().doubleValue(),
                stockData.changePercent().doubleValue(),
                stockData.tradingVolume(),
                stockData.tradingAmount(),
                stockData.priceHigh52Week().doubleValue(),
                stockData.priceLow52Week().doubleValue(),
                calculatePrice52WeekPercentage(stockData),
                formatDailyPrices(stockData.dailyPricesJson())
        );
    }

    /**
     * 52주 최저가 대비 현재가 비율
     */
    private double calculatePrice52WeekPercentage(StockDataDto stockData) {
        double low = stockData.priceLow52Week().doubleValue();
        double current = stockData.currentPrice().doubleValue();
        return ((current - low) / low) * 100;
    }

    /**
     * 일봉 데이터 포맷팅
     */
    private String formatDailyPrices(String dailyPricesJson) {
        // 최근 10일만 표시
        try {
            List<Map<String, Object>> prices = objectMapper.readValue(dailyPricesJson, List.class);
            StringBuilder sb = new StringBuilder();

            int count = Math.min(10, prices.size());
            for (int i = 0; i < count; i++) {
                Map<String, Object> price = prices.get(i);
                sb.append(String.format(
                        "%s: 시가 %s, 종가 %s, 고가 %s, 저가 %s, 거래량 %s\n",
                        price.get("tradeDate"),
                        price.get("openPrice"),
                        price.get("closePrice"),
                        price.get("highPrice"),
                        price.get("lowPrice"),
                        price.get("volume")
                ));
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("일봉 데이터 포맷팅 실패", e);
            return dailyPricesJson;
        }
    }


    private LLMAnalysisResponseDto parseLLMResponse(String fullText) {

        try {
            // 마지막 JSON 시작 위치 찾기
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
