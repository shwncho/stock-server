package com.stock.stockserver.infrastructure.external;

import com.stock.stockserver.dto.StockDataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
    public String analyzeStock(StockDataDto stockData) {
        if ("claude".equalsIgnoreCase(provider)) {
            return analyzeWithClaude(stockData);
        } else if ("gpt".equalsIgnoreCase(provider)) {
            return analyzeWithGPT(stockData);
        } else {
            log.error("알 수 없는 LLM provider: {}", provider);
            return null;
        }
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
                다음은 한국 주식 시장의 거래량 상위 종목 중 하나의 데이터입니다. \
                기술적 분석을 통해 투자 의견을 제시해주세요.
                
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
                
                [분석 요청사항]
                1. 위 데이터를 기반으로 기술적 분석 (추세, 지지선/저항선 등)
                2. 거래량 분석 (거래량 패턴 및 의미)
                3. 가격 변동 패턴 분석
                4. 투자 의견 제시 (매수/매도/보유)
                5. 투자 근거 및 주의사항
                
                상세하고 전문적인 분석을 부탁드립니다.
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
}
