package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.infrastructure.persistence.RedisRepository;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KisApiClientRateLimitTest {

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private RedisRepository redisRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 토큰 발급은 본 테스트의 관심사가 아니므로 Redis 캐시 hit으로 우회
        when(redisRepository.get("kis:access-token")).thenReturn("cached-token");

        when(webClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        // 기본은 성공 응답 — 시나리오별로 개별 stub 으로 덮어쓴다
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"output\":[]}"));
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private KisApiClient buildClient(RateLimiter rl, Retry rt) {
        KisApiClient client = new KisApiClient(webClient, objectMapper, redisRepository, rl, rt);
        ReflectionTestUtils.setField(client, "baseUrl", "https://test.kis");
        ReflectionTestUtils.setField(client, "appKey", "test-key");
        ReflectionTestUtils.setField(client, "appSecret", "test-secret");
        return client;
    }

    private RateLimiter rateLimiter(int limit, long refreshMs, long timeoutMs) {
        return RateLimiter.of("rl-" + System.nanoTime(),
                RateLimiterConfig.custom()
                        .limitForPeriod(limit)
                        .limitRefreshPeriod(Duration.ofMillis(refreshMs))
                        .timeoutDuration(Duration.ofMillis(timeoutMs))
                        .build());
    }

    private Retry retry(int maxAttempts, long initialWaitMs) {
        return Retry.of("rt-" + System.nanoTime(),
                RetryConfig.custom()
                        .maxAttempts(maxAttempts)
                        .intervalFunction(IntervalFunction.ofExponentialBackoff(initialWaitMs, 2.0))
                        .retryOnException(this::isRetryable)
                        .build());
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof WebClientResponseException w) {
            return w.getStatusCode().is5xxServerError() || w.getStatusCode().value() == 429;
        }
        return false;
    }

    private WebClientResponseException httpError(HttpStatus status, String body) {
        return WebClientResponseException.create(
                status.value(), status.getReasonPhrase(), null,
                body.getBytes(), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeCallApi(KisApiClient client) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "callApi", "GET", "/endpoint", "p=1", "TR001");
    }

    // ───────────────────────────── tests ─────────────────────────────

    @Test
    @DisplayName("callApi - RateLimiter 한도(20건/초) 이내 호출은 모두 성공한다")
    void callApi_withinRateLimit_allSucceed() {
        // KIS 한도와 동일하게 초당 20건으로 설정
        KisApiClient client = buildClient(rateLimiter(20, 1000, 0), retry(3, 50));

        for (int i = 0; i < 20; i++) {
            Map<String, Object> result = invokeCallApi(client);
            assertNotNull(result);
        }

        verify(webClient, times(20)).method(any());
    }

    @Test
    @DisplayName("callApi - 초당 20건 초과 시 RequestNotPermitted 발생하고 재시도되지 않는다")
    void callApi_exceedsRateLimit_throwsRequestNotPermittedAndDoesNotRetry() {
        // timeout=0: permit 부족 시 즉시 실패
        KisApiClient client = buildClient(rateLimiter(20, 10_000, 0), retry(3, 50));

        for (int i = 0; i < 20; i++) {
            invokeCallApi(client);
        }

        // 21번째 호출은 RateLimiter에 의해 차단됨
        assertThrows(RequestNotPermitted.class, () -> invokeCallApi(client));

        // RequestNotPermitted은 retryable이 아니므로 webClient 호출은 정확히 20번
        verify(webClient, times(20)).method(any());
    }

    @Test
    @DisplayName("callApi - 5xx 에러 발생 시 retry로 재시도하여 3차 시도에 성공한다")
    void callApi_serverError_retriesAndSucceedsOnThirdAttempt() {
        KisApiClient client = buildClient(rateLimiter(100, 1000, 5000), retry(3, 50));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "500")))
                .thenReturn(Mono.error(httpError(HttpStatus.BAD_GATEWAY, "502")))
                .thenReturn(Mono.just("{\"output\":[]}"));

        Map<String, Object> result = invokeCallApi(client);

        assertNotNull(result);
        verify(webClient, times(3)).method(any());
    }

    @Test
    @DisplayName("callApi - 5xx 에러가 max-attempts(3회) 끝까지 반복되면 예외가 전파된다")
    void callApi_serverError_retryExhaustedAfterMaxAttempts() {
        KisApiClient client = buildClient(rateLimiter(100, 1000, 5000), retry(3, 50));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "EGW00000")));

        WebClientResponseException ex = assertThrows(
                WebClientResponseException.class,
                () -> invokeCallApi(client));

        assertEquals(500, ex.getStatusCode().value());
        // max-attempts=3 → 최초 1회 + 재시도 2회 = 정확히 3회 호출
        verify(webClient, times(3)).method(any());
    }

    @Test
    @DisplayName("callApi - 429 Too Many Requests(EGW00201 케이스) 응답은 retry 대상이 되어 재시도된다")
    void callApi_tooManyRequests_retriesOn429() {
        KisApiClient client = buildClient(rateLimiter(100, 1000, 5000), retry(3, 50));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.TOO_MANY_REQUESTS, "EGW00201")))
                .thenReturn(Mono.just("{\"output\":[]}"));

        Map<String, Object> result = invokeCallApi(client);

        assertNotNull(result);
        verify(webClient, times(2)).method(any());
    }

    @Test
    @DisplayName("callApi - 4xx 클라이언트 에러(400)는 retry 대상이 아니므로 즉시 전파된다")
    void callApi_clientError_doesNotRetryOn400() {
        KisApiClient client = buildClient(rateLimiter(100, 1000, 5000), retry(3, 50));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.BAD_REQUEST, "bad request")));

        WebClientResponseException ex = assertThrows(
                WebClientResponseException.class,
                () -> invokeCallApi(client));

        assertEquals(400, ex.getStatusCode().value());
        // 재시도 없이 1회만 호출
        verify(webClient, times(1)).method(any());
    }

    @Test
    @DisplayName("callApi - 지수 백오프: 5xx 두 번 후 성공 시 누적 대기 시간이 initial+initial*2 이상")
    void callApi_exponentialBackoff_appliesCumulativeWaitTime() {
        long initialWaitMs = 200;
        KisApiClient client = buildClient(rateLimiter(100, 1000, 5000), retry(3, initialWaitMs));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "500")))
                .thenReturn(Mono.error(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "500")))
                .thenReturn(Mono.just("{\"output\":[]}"));

        long start = System.currentTimeMillis();
        invokeCallApi(client);
        long elapsed = System.currentTimeMillis() - start;

        // 1차 대기 200ms + 2차 대기 400ms = 최소 600ms
        long expectedMin = initialWaitMs + initialWaitMs * 2;
        assertTrue(elapsed >= expectedMin - 50,
                "지수 백오프 대기 시간이 누적되어야 합니다. expected>=" + expectedMin + "ms, actual=" + elapsed + "ms");
        verify(webClient, times(3)).method(any());
    }

    @Test
    @DisplayName("callApi - RateLimiter 한도 도달 후 refresh-period 경과 시 permit이 재충전되어 다시 호출 가능")
    void callApi_rateLimiter_refreshAfterPeriodRestoresPermits() throws InterruptedException {
        long refreshMs = 200;
        KisApiClient client = buildClient(rateLimiter(2, refreshMs, 0), retry(3, 50));

        invokeCallApi(client);
        invokeCallApi(client);

        // 같은 윈도우 내 3번째 호출은 timeout=0 이므로 즉시 실패
        assertThrows(RequestNotPermitted.class, () -> invokeCallApi(client));

        // refresh 윈도우 경계 너머까지 대기
        Thread.sleep(refreshMs + 100);

        // 새 윈도우에서는 다시 permit 사용 가능
        Map<String, Object> result = invokeCallApi(client);
        assertNotNull(result);

        // 성공한 3건만 webClient에 도달 (실패한 1건은 RateLimiter 단계에서 차단됨)
        verify(webClient, times(3)).method(any());
    }

    @Test
    @DisplayName("callApi - RateLimiter timeout-ms 안에 permit이 회복되면 대기 후 정상 처리")
    void callApi_rateLimiter_waitsForPermitWithinTimeout() {
        // timeout(1000ms) > refresh(200ms): permit 부족 시 다음 refresh까지 대기
        KisApiClient client = buildClient(rateLimiter(2, 200, 1000), retry(3, 50));

        invokeCallApi(client);
        invokeCallApi(client);

        // 3번째는 즉시 실패하지 않고 대기 후 성공해야 함
        Map<String, Object> result = invokeCallApi(client);
        assertNotNull(result);

        verify(webClient, times(3)).method(any());
    }

    @Test
    @DisplayName("callApi - 재시도가 일어날 때마다 RateLimiter permit이 새로 소비된다 (Retry > RateLimiter 합성)")
    void callApi_retryConsumesRateLimiterPermitOnEachAttempt() {
        // permit 3개만 발급 — 3회 재시도가 정확히 다 소비
        KisApiClient client = buildClient(rateLimiter(3, 10_000, 0), retry(3, 50));

        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(httpError(HttpStatus.INTERNAL_SERVER_ERROR, "500")));

        // 1차 외부 호출 → 3회 재시도 모두 500 → 3개 permit 모두 소진 후 500 전파
        WebClientResponseException ex = assertThrows(
                WebClientResponseException.class,
                () -> invokeCallApi(client));
        assertEquals(500, ex.getStatusCode().value());
        verify(webClient, times(3)).method(any());

        // 2차 외부 호출 → 남은 permit 없음 → 첫 시도부터 RateLimiter 차단
        assertThrows(RequestNotPermitted.class, () -> invokeCallApi(client));

        // 2차 호출은 WebClient까지 도달하지 못하므로 누적 호출 수는 여전히 3
        verify(webClient, times(3)).method(any());
    }
}
