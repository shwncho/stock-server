package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stockserver.infrastructure.persistence.RedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KisApiClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisRepository redisRepository;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private KisApiClient kisApiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kisApiClient, "baseUrl", "https://openapi.koreainvestment.com");
        ReflectionTestUtils.setField(kisApiClient, "appKey", "test-app-key");
        ReflectionTestUtils.setField(kisApiClient, "appSecret", "test-app-secret");
    }

    @SuppressWarnings("unchecked")
    private void setupWebClientPost(Map<?, ?> responseBody) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(responseBody));
    }

    @Test
    @DisplayName("getAccessToken - 성공적인 토큰 발급")
    void getAccessToken_success() throws Exception {
        when(redisRepository.get("kis:access-token")).thenReturn(null);
        setupWebClientPost(Map.of("access_token", "test-access-token", "expires_in", 86400));

        String token = kisApiClient.getAccessToken();

        assertNotNull(token);
        assertEquals("test-access-token", token);
        verify(redisRepository).set(eq("kis:access-token"), eq("test-access-token"), eq(Duration.ofHours(6)));
    }

    @Test
    @DisplayName("getAccessToken - 응답에 access_token 없을 시 예외 발생")
    void getAccessToken_missingToken() throws Exception {
        when(redisRepository.get("kis:access-token")).thenReturn(null);
        setupWebClientPost(Map.of("error", "internal_error"));

        assertThrows(RuntimeException.class, () -> kisApiClient.getAccessToken());
    }

    @Test
    @DisplayName("getAccessToken - WebClient 호출 실패 시 예외 발생")
    @SuppressWarnings("unchecked")
    void getAccessToken_webClientFailure() {
        when(redisRepository.get("kis:access-token")).thenReturn(null);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("API 호출 실패")));

        assertThrows(RuntimeException.class, () -> kisApiClient.getAccessToken());
    }

    @Test
    @DisplayName("getAccessToken - 캐시된 토큰이 유효한 경우 API 호출 없이 반환")
    void getAccessToken_returnsCachedToken() throws Exception {
        when(redisRepository.get("kis:access-token")).thenReturn("cached-token");

        String token = kisApiClient.getAccessToken();

        assertEquals("cached-token", token);
        verify(redisRepository, never()).set(any(), any(), any(Duration.class));
        verify(webClient, never()).post();
    }

    @Test
    @DisplayName("getAccessToken - 캐시 없는 경우 새로 발급 후 저장")
    void getAccessToken_fetchesWhenNoCachedToken() throws Exception {
        when(redisRepository.get("kis:access-token")).thenReturn(null);
        setupWebClientPost(Map.of("access_token", "new-token", "expires_in", 86400));

        String token = kisApiClient.getAccessToken();

        assertNotNull(token);
        assertEquals("new-token", token);
        verify(redisRepository).set(eq("kis:access-token"), eq("new-token"), eq(Duration.ofHours(6)));
    }
}
