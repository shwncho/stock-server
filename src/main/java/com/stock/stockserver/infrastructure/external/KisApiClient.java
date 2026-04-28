package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.domain.AnalysisTarget;
import com.stock.stockserver.dto.DailyPriceDto;
import com.stock.stockserver.dto.VolumeRankDto;
import com.stock.stockserver.infrastructure.persistence.RedisRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.DayOfWeek;
import java.time.Duration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.springframework.http.HttpMethod.valueOf;

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

    @Value("${analysis.overseas.exchanges:NAS,NYS,AMS}")
    private List<String> overseasExchanges;

    private final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String ACCESS_TOKEN_KEY = "kis:access-token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(6);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RedisRepository redisRepository;
    private final RateLimiter kisRateLimiter;
    private final Retry kisApiRetry;


    /**
     * 거래량 순위 조회 (Top 10)
     */
    @Cacheable(cacheNames = "kisVolumeRankCache")
    public List<VolumeRankDto> getVolumeRankStocks() {
        return getVolumeRankStocks(AnalysisTarget.DOMESTIC);
    }

    public List<VolumeRankDto> getVolumeRankStocks(AnalysisTarget target) {
        if (target == AnalysisTarget.OVERSEAS) {
            return getOverseasVolumeRankStocks();
        }
        return getDomesticVolumeRankStocks();
    }

    @Cacheable(cacheNames = "kisDomesticVolumeRankCache")
    public List<VolumeRankDto> getDomesticVolumeRankStocks() {
        String endpoint = "/uapi/domestic-stock/v1/quotations/volume-rank";
        String trId = "FHPST01710000";

        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_COND_SCR_DIV_CODE", "20171");
        params.put("FID_INPUT_ISCD", "0002");
        params.put("FID_DIV_CLS_CODE", "0");
        params.put("FID_BLNG_CLS_CODE", "0");
        params.put("FID_TRGT_CLS_CODE", "111111111");
        params.put("FID_TRGT_EXLS_CLS_CODE", "000000");
        params.put("FID_INPUT_PRICE_1", "0");
        params.put("FID_INPUT_PRICE_2", "0");
        params.put("FID_VOL_CNT", "0");
        params.put("FID_INPUT_DATE_1", "0");

        try {
            String queryString = buildQueryString(params);
            Map<String, Object> response = callApi("GET", endpoint, queryString, trId);

            List<VolumeRankDto> results = new ArrayList<>();
            if (response.containsKey("output")) {
                List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output");

                for (int i = 0; i < Math.min(output.size(), 10); i++) {
                    Map<String, Object> item = output.get(i);

                    VolumeRankDto rank = VolumeRankDto.builder()
                            .target(AnalysisTarget.DOMESTIC)
                            .exchangeCode("KRX")
                            .stockCode((String) item.get("mksc_shrn_iscd"))
                            .stockName((String) item.get("hts_kor_isnm"))
                            .currentPrice(parseDouble(item.get("stck_prpr")))
                            .changePercent(parseDouble(item.get("prdy_ctrt")))
                            .tradingVolume(parseLong(item.get("acml_vol")))
                            .tradingAmount(parseLong(item.get("acml_tr_pbmn")))
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
     * 해외주식 거래량 순위 조회 (Top 10)
     *
     * KIS 해외 거래량순위 API는 거래소 단위 조회라 기본 미국 주요 거래소(NAS/NYS/AMS)를 조회한 뒤
     * 거래량 기준으로 합산 Top 10을 만든다.
     */
    @Cacheable(
            cacheNames = "kisOverseasVolumeRankCache",
            key = "#root.target.getOverseasVolumeRankCacheKey()"
    )
    public List<VolumeRankDto> getOverseasVolumeRankStocks() {
        String endpoint = "/uapi/overseas-stock/v1/ranking/trade-vol";
        String trId = "HHDFS76310010";

        List<VolumeRankDto> results = new ArrayList<>();
        log.info("해외주식 거래량 순위 조회 시작: exchanges={}", overseasExchanges);

        for (String exchange : overseasExchanges) {
            Map<String, String> params = new HashMap<>();
            params.put("EXCD", exchange);
            params.put("NDAY", "0");
            params.put("VOL_RANG", "0");
            params.put("KEYB", "");
            params.put("AUTH", "");
            params.put("PRC1", "");
            params.put("PRC2", "");

            try {
                String queryString = buildQueryString(params);
                Map<String, Object> response = callApi("GET", endpoint, queryString, trId);

                if (response.containsKey("output2")) {
                    List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output2");
                    log.info("해외주식 거래량 순위 조회 완료: exchange={}, count={}", exchange, output.size());

                    for (Map<String, Object> item : output) {
                        results.add(VolumeRankDto.builder()
                                .target(AnalysisTarget.OVERSEAS)
                                .exchangeCode(valueAsString(item.getOrDefault("excd", exchange)))
                                .stockCode(valueAsString(item.get("symb")))
                                .stockName(valueAsString(item.get("name")))
                                .currentPrice(parseDouble(item.get("last")))
                                .changePercent(parseDouble(item.get("rate")))
                                .tradingVolume(parseLong(item.get("tvol")))
                                .tradingAmount(parseLong(item.get("tamt")))
                                .rank(parseInteger(item.get("rank")))
                                .build());
                    }
                }
            } catch (Exception e) {
                log.error("해외주식 거래량 순위 조회 실패: exchange={}", exchange, e);
            }
        }

        List<VolumeRankDto> sortedResults = results.stream()
                .filter(rank -> rank.stockCode() != null && !rank.stockCode().isBlank())
                .sorted(Comparator.comparing(VolumeRankDto::tradingVolume, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        List<VolumeRankDto> topResults = new ArrayList<>();
        for (int i = 0; i < sortedResults.size(); i++) {
            VolumeRankDto rank = sortedResults.get(i);
            topResults.add(VolumeRankDto.builder()
                        .target(rank.target())
                        .exchangeCode(rank.exchangeCode())
                        .stockCode(rank.stockCode())
                        .stockName(rank.stockName())
                        .currentPrice(rank.currentPrice())
                        .changePercent(rank.changePercent())
                        .tradingVolume(rank.tradingVolume())
                        .tradingAmount(rank.tradingAmount())
                        .rank(i + 1)
                        .build());
        }

        log.info("해외주식 거래량 Top 10 조회 성공: exchanges={}, selectedResults={}", overseasExchanges, topResults);
        return topResults;
    }

    public String getOverseasVolumeRankCacheKey() {
        return LocalDate.now() + ":" + String.join(",", overseasExchanges);
    }

    /**
     * 일봉 데이터 조회
     */
    @Cacheable(
            cacheNames = "kisDailyCache",
            key = "#stockCode + ':' + #days + ':' + T(java.time.LocalDate).now()"
    )
    public List<DailyPriceDto> getDailyData(String stockCode, int days) {
        return getDailyData(AnalysisTarget.DOMESTIC, "KRX", stockCode, days);
    }

    public List<DailyPriceDto> getDailyData(AnalysisTarget target, String exchangeCode, String stockCode, int days) {
        if (target == AnalysisTarget.OVERSEAS) {
            return getOverseasDailyData(exchangeCode, stockCode, days);
        }
        return getDomesticDailyData(stockCode, days);
    }

    @Cacheable(
            cacheNames = "kisDomesticDailyCache",
            key = "#stockCode + ':' + #days + ':' + T(java.time.LocalDate).now()"
    )
    public List<DailyPriceDto> getDomesticDailyData(String stockCode, int days) {
        String endpoint = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
        String trId = "FHKST03010100";

        LocalDate endDate = getLastTradingDate();          // FID_INPUT_DATE_2
        LocalDate startDate = endDate.minusDays(days - 1); // FID_INPUT_DATE_1

        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_INPUT_ISCD", stockCode);
        params.put("FID_INPUT_DATE_1",
                startDate.format(DateTimeFormatter.BASIC_ISO_DATE));
        params.put("FID_INPUT_DATE_2",
                endDate.format(DateTimeFormatter.BASIC_ISO_DATE));
        params.put("FID_PERIOD_DIV_CODE", "D");
        params.put("FID_ORG_ADJ_PRC", "0");

        try {
            String queryString = buildQueryString(params);
            Map<String, Object> response = callApi("GET", endpoint, queryString, trId);

            List<DailyPriceDto> results = new ArrayList<>();

            if (response.containsKey("output2")) {
                List<Map<String, Object>> output =
                        (List<Map<String, Object>>) response.get("output2");

                for (Map<String, Object> item : output) {
                    LocalDate tradeDate = LocalDate.parse(
                            (String) item.get("stck_bsop_date"),
                            DateTimeFormatter.BASIC_ISO_DATE
                    );

                    results.add(DailyPriceDto.builder()
                            .stockCode(stockCode)
                            .tradeDate(tradeDate)
                            .openPrice(Double.parseDouble((String) item.get("stck_oprc")))
                            .closePrice(Double.parseDouble((String) item.get("stck_clpr")))
                            .highPrice(Double.parseDouble((String) item.get("stck_hgpr")))
                            .lowPrice(Double.parseDouble((String) item.get("stck_lwpr")))
                            .volume(Long.parseLong((String) item.get("acml_vol")))
                            .build());
                }
            }

            log.info("일봉 조회 성공: {} ~ {}", startDate, endDate);
            return results;

        } catch (Exception e) {
            log.error("일봉 데이터 조회 실패: {}", stockCode, e);
            return List.of();
        }
    }

    @Cacheable(
            cacheNames = "kisOverseasDailyCache",
            key = "#exchangeCode + ':' + #stockCode + ':' + #days + ':' + T(java.time.LocalDate).now()"
    )
    public List<DailyPriceDto> getOverseasDailyData(String exchangeCode, String stockCode, int days) {
        String endpoint = "/uapi/overseas-price/v1/quotations/dailyprice";
        String trId = "HHDFS76240000";

        Map<String, String> params = new HashMap<>();
        params.put("AUTH", "");
        params.put("EXCD", exchangeCode);
        params.put("SYMB", stockCode);
        params.put("GUBN", "0");
        params.put("BYMD", "");
        params.put("MODP", "0");

        try {
            String queryString = buildQueryString(params);
            Map<String, Object> response = callApi("GET", endpoint, queryString, trId);

            List<DailyPriceDto> results = new ArrayList<>();

            if (response.containsKey("output2")) {
                List<Map<String, Object>> output =
                        (List<Map<String, Object>>) response.get("output2");

                for (Map<String, Object> item : output) {
                    LocalDate tradeDate = LocalDate.parse(
                            valueAsString(item.get("xymd")),
                            DateTimeFormatter.BASIC_ISO_DATE
                    );

                    results.add(DailyPriceDto.builder()
                            .stockCode(stockCode)
                            .tradeDate(tradeDate)
                            .openPrice(parseDouble(item.get("open")))
                            .closePrice(parseDouble(item.get("clos")))
                            .highPrice(parseDouble(item.get("high")))
                            .lowPrice(parseDouble(item.get("low")))
                            .volume(parseLong(item.get("tvol")))
                            .build());
                }
            }

            log.info("해외주식 일봉 조회 성공: exchange={}, stockCode={}, count={}",
                    exchangeCode, stockCode, results.size());
            return results.stream().limit(days).toList();

        } catch (Exception e) {
            log.error("해외주식 일봉 데이터 조회 실패: exchange={}, stockCode={}", exchangeCode, stockCode, e);
            return List.of();
        }
    }

    private LocalDate getLastTradingDate() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 장중이면 오늘 봉은 미확정
        if (now.isBefore(LocalTime.of(15, 30))) {
            today = today.minusDays(1);
        }

        // 주말 보정
        while (today.getDayOfWeek() == DayOfWeek.SATURDAY
                || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            today = today.minusDays(1);
        }

        return today;
    }

    private Map<String, Object> callApi(String method, String endpoint,
                                        String queryString, String trId)
            throws Exception {

        String fullUrl = queryString != null && !queryString.isEmpty()
                ? baseUrl + endpoint + "?" + queryString
                : baseUrl + endpoint;

        String accessToken = getAccessToken();

        try {
            String responseBody = Retry.decorateSupplier(kisApiRetry,
                    RateLimiter.decorateSupplier(kisRateLimiter,
                            () -> executeApiCall(method, fullUrl, endpoint, trId, accessToken)
                    )
            ).get();

            return objectMapper.readValue(responseBody, Map.class);
        } catch (WebClientResponseException e) {
            log.error("API 호출 실패: method={}, endpoint={}, trId={}, status={}, body={}",
                    method, endpoint, trId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw e;
        } catch (Exception e) {
            log.error("API 호출 실패: {} {}", method, endpoint, e);
            throw e;
        }
    }

    private String executeApiCall(String method, String fullUrl, String endpoint, String trId, String accessToken) {
        return webClient.method(valueOf(method))
                .uri(fullUrl)
                .header("Authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", trId)
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(WebClientResponseException.class, e ->
                        log.warn("KIS API 응답 오류: endpoint={}, trId={}, status={}, body={}",
                                endpoint, trId, e.getStatusCode(), e.getResponseBodyAsString()))
                .block();
    }

    public String getAccessToken() {
        String cachedToken = redisRepository.get(ACCESS_TOKEN_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }
        return fetchNewAccessToken();
    }

    private String fetchNewAccessToken() {
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
        );

        log.info("KIS 액세스 토큰 발급 요청: {}", baseUrl + TOKEN_PATH);

        Map<?, ?> response = webClient.post()
                .uri(baseUrl + TOKEN_PATH)
                .header("content-type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("KIS 토큰 발급 응답이 없습니다.");
        }

        String accessToken = (String) response.get("access_token");

        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("KIS access token 응답에 access_token이 없습니다.");
        }

        redisRepository.set(ACCESS_TOKEN_KEY, accessToken, ACCESS_TOKEN_TTL);
        return accessToken;
    }

    private String buildQueryString(Map<String, String> params) {
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

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double parseDouble(Object value) {
        String text = valueAsString(value);
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(text.replace(",", ""));
    }

    private Long parseLong(Object value) {
        String text = valueAsString(value);
        if (text == null || text.isBlank()) {
            return 0L;
        }
        return new java.math.BigDecimal(text.replace(",", "")).longValue();
    }

    private Integer parseInteger(Object value) {
        String text = valueAsString(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text.replace(",", ""));
    }
}
