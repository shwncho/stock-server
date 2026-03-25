package com.stock.stockserver.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
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
    private okhttp3.Call mockCall;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private KisApiClient kisApiClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kisApiClient, "baseUrl", "https://openapi.koreainvestment.com");
        ReflectionTestUtils.setField(kisApiClient, "appKey", "test-app-key");
        ReflectionTestUtils.setField(kisApiClient, "appSecret", "test-app-secret");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void setupMockOkHttpClient(String responseBody, int responseCode) throws IOException {
        OkHttpClient mockedClient = mock(OkHttpClient.class);
        
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                "{\"grant_type\":\"client_credentials\",\"appkey\":\"test-app-key\",\"appsecret\":\"test-app-secret\"}"
        );
        
        Request request = new Request.Builder()
                .url("https://openapi.koreainvestment.com/oauth2/tokenP")
                .post(requestBody)
                .build();

        ResponseBody body = ResponseBody.create(responseBody, MediaType.parse("application/json"));
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("OK")
                .body(body)
                .build();

        when(mockedClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(response);
        
        ReflectionTestUtils.setField(kisApiClient, "okHttpClient", mockedClient);
    }

    @Test
    @DisplayName("getAccessToken - 성공적인 토큰 발급")
    void getAccessToken_success() throws Exception {
        String mockResponse = "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":86400}";
        setupMockOkHttpClient(mockResponse, 200);
        when(valueOperations.get("kis:access-token")).thenReturn(null);
        when(objectMapper.readValue(mockResponse, Map.class))
                .thenReturn(Map.of("access_token", "test-access-token", "expires_in", 86400));

        String token = kisApiClient.getAccessToken();

        assertNotNull(token);
        assertEquals("test-access-token", token);
        verify(valueOperations).set(eq("kis:access-token"), eq("test-access-token"), eq(Duration.ofHours(6)));
    }

    @Test
    @DisplayName("getAccessToken - API 호출 실패 시 예외 발생")
    void getAccessToken_apiFailure() throws Exception {
        String mockResponse = "{\"error\":\"internal_error\"}";
        setupMockOkHttpClient(mockResponse, 500);
        when(valueOperations.get("kis:access-token")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> kisApiClient.getAccessToken());
    }

    @Test
    @DisplayName("getCachedAccessToken - 캐시된 토큰이 유효한 경우")
    void getCachedAccessToken_validToken() throws Exception {
        when(valueOperations.get("kis:access-token")).thenReturn("cached-token");

        String token = kisApiClient.getCachedAccessToken();

        assertEquals("cached-token", token);
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("getCachedAccessToken - 캐시된 토큰이 없는 경우 새로 발급")
    void getCachedAccessToken_noCachedToken() throws Exception {
        String mockResponse = "{\"access_token\":\"new-token\",\"token_type\":\"Bearer\",\"expires_in\":86400}";
        setupMockOkHttpClient(mockResponse, 200);
        when(valueOperations.get("kis:access-token")).thenReturn(null);
        when(objectMapper.readValue(mockResponse, Map.class))
                .thenReturn(Map.of("access_token", "new-token", "expires_in", 86400));

        String token = kisApiClient.getCachedAccessToken();

        assertNotNull(token);
        assertEquals("new-token", token);
    }

    @Test
    @DisplayName("getCachedAccessToken - 토큰이 만료된 경우 새로 발급")
    void getCachedAccessToken_expiredToken() throws Exception {
        String mockResponse = "{\"access_token\":\"new-token\",\"token_type\":\"Bearer\",\"expires_in\":86400}";
        setupMockOkHttpClient(mockResponse, 200);
        when(valueOperations.get("kis:access-token")).thenReturn(null);
        when(objectMapper.readValue(mockResponse, Map.class))
                .thenReturn(Map.of("access_token", "new-token", "expires_in", 86400));

        String token = kisApiClient.getCachedAccessToken();

        assertNotNull(token);
        assertEquals("new-token", token);
    }
}
