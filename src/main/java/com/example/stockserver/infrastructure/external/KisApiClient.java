package com.example.stockserver.infrastructure.external;

import com.example.stockserver.dto.DailyPriceDto;
import com.example.stockserver.dto.VolumeRankDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import okhttp3.*;
import java.time.Instant;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisApiClient {

    @Value("${kis.api.base-url}")
    private String baseUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    private final String TOKEN_PATH = "/oauth2/tokenP";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String cachedAccessToken;
    private Instant tokenExpirationTime;


    /**
     * 거래량 순위 조회 (Top 10)
     */
    public List<VolumeRankDto> getVolumeRankStocks() {
        String endpoint = "/uapi/domestic-stock/v1/quotations/volume-rank";

        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_COND_SCR_DIV_CODE", "20171");
        params.put("FID_INPUT_DATE_1", LocalDate.now().toString().replace("-", ""));
        params.put("FID_INPUT_DATE_2", LocalDate.now().toString().replace("-", ""));
        params.put("FID_DIV_CLS_CODE", "0");
        params.put("FID_BLUD_DIV_CODE", "0");
        params.put("FID_RSFL_CLS_CODE", "0");
        params.put("FID_CPS_EPS_DVS_CODE", "0");
        params.put("FID_ITEM_CONT_NXT_PG_KEY", "");
        params.put("FID_PRC_CLSF_CODE", "0");
        params.put("FID_SORT_CLSF_CODE", "1");
        params.put("FID_INPUT_HOUR_1", "");

        try {
            String queryString = buildQueryString(params);
            Map<String, Object> response = callApi("GET", endpoint, queryString, null);

            List<VolumeRankDto> results = new ArrayList<>();
            if (response.containsKey("output")) {
                List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output");

                for (int i = 0; i < Math.min(output.size(), 10); i++) {
                    Map<String, Object> item = output.get(i);

                    VolumeRankDto rank = VolumeRankDto.builder()
                            .stockCode((String) item.get("mksc_shrn_iscd"))
                            .stockName((String) item.get("hts_kor_isnm"))
                            .currentPrice(Double.parseDouble((String) item.get("stck_prpr")))
                            .changePercent(Double.parseDouble((String) item.get("prdy_ctrt")))
                            .tradingVolume(Long.parseLong((String) item.get("acml_vol")))
                            .tradingAmount(Long.parseLong((String) item.get("acml_tr_pbmn")))
                            .rank(i + 1)
                            .build();

                    results.add(rank);
                }
            }

            log.info("거래량 Top 10 조회 성공");
            log.info("KIS Response: {}", results);
            return results;

        } catch (Exception e) {
            log.error("거래량 순위 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 일봉 데이터 조회
     */
    public List<DailyPriceDto> getDailyData(String stockCode, int days) {
        String endpoint = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_INPUT_ISCD", stockCode);
        params.put("FID_INPUT_DATE_1", LocalDate.now().minusDays(days).toString().replace("-", ""));
        params.put("FID_INPUT_DATE_2", LocalDate.now().toString().replace("-", ""));
        params.put("FID_PERIOD_DIV_CODE", "D");
        params.put("FID_ORG_ADJ_PRC", "0");
        params.put("FID_ADJ_PRC_APLY_CD", "1");

        try {
            String queryString = buildQueryString(params);
            Map<String, Object> response = callApi("GET", endpoint, queryString, null);

            List<DailyPriceDto> results = new ArrayList<>();
            if (response.containsKey("output2")) {
                List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output2");

                output.forEach(item -> {
                    String dateStr = (String) item.get("candle_date_time_kst");
                    LocalDate tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));

                    DailyPriceDto daily = DailyPriceDto.builder()
                            .stockCode(stockCode)
                            .tradeDate(tradeDate)
                            .openPrice(Double.parseDouble((String) item.get("opening_price")))
                            .closePrice(Double.parseDouble((String) item.get("stck_clpr")))
                            .highPrice(Double.parseDouble((String) item.get("high_price")))
                            .lowPrice(Double.parseDouble((String) item.get("low_price")))
                            .volume(Long.parseLong((String) item.get("cntg_vol")))
                            .build();

                    results.add(daily);
                });
            }

            log.debug("일봉 데이터 조회: stockCode={}", stockCode);
            return results;

        } catch (Exception e) {
            log.error("일봉 데이터 조회 실패: {}", stockCode, e);
            return List.of();
        }
    }

    private Map<String, Object> callApi(String method, String endpoint,
                                        String queryString, Map<String, Object> body)
            throws Exception {

        String fullUrl = queryString != null && !queryString.isEmpty()
                ? baseUrl + endpoint + "?" + queryString
                : baseUrl + endpoint;

        String accessToken = getCachedAccessToken();

        try {
            String responseBody = webClient.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(fullUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", "FHPST01710000")
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(responseBody, Map.class);

        } catch (Exception e) {
            log.error("API 호출 실패: {} {}", method, endpoint, e);
            throw e;
        }
    }

    public String getAccessToken() throws Exception {
        if (isTokenValid()) {
            return cachedAccessToken;
        }

        OkHttpClient client = new OkHttpClient().newBuilder().build();

        String jsonBody = String.format(
                "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                appKey, appSecret
        );

        log.info("Request URL: {}", baseUrl + TOKEN_PATH);
        log.info("Request Body: {}", jsonBody);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                jsonBody
        );

        Request request = new Request.Builder()
                .url(baseUrl + TOKEN_PATH)
                .post(body)
                .addHeader("content-type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            log.info("Response Code: {}", response.code());
            log.info("Response Body: {}", responseBody);

            if (!response.isSuccessful()) {
                throw new RuntimeException("API 호출 실패: " + response.code() + ", Body: " + responseBody);
            }

            cachedAccessToken = responseBody.split("\"access_token\":\"")[1].split("\"")[0];
            tokenExpirationTime = Instant.now().plusSeconds(86400); // 토큰 유효 기간을 24시간으로 설정
            return cachedAccessToken;
        } catch (Exception e) {
            log.error("토큰 발급 중 오류 발생", e);
            throw e;
        }
    }

    public String getCachedAccessToken() throws Exception {
        if (isTokenValid()) {
            return cachedAccessToken;
        } else {
            return getAccessToken(); // 토큰이 만료되었거나 없으면 새로 발급
        }
    }

    private boolean isTokenValid() {
        return cachedAccessToken != null && Instant.now().isBefore(tokenExpirationTime);
    }

    private String buildQueryString(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(entry.getKey())
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return sb.toString();
    }
}
