package com.stock.stockserver.infrastructure.config;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Configuration
@Slf4j
public class KisResilienceConfig {

    @Bean
    public RateLimiter kisRateLimiter(
            @Value("${kis.api.resilience.rate-limit.limit-for-period:5}") int limitForPeriod,
            @Value("${kis.api.resilience.rate-limit.refresh-period-ms:1000}") long refreshPeriodMs,
            @Value("${kis.api.resilience.rate-limit.timeout-ms:5000}") long timeoutMs
    ) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(Duration.ofMillis(refreshPeriodMs))
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .build();

        RateLimiter rateLimiter = RateLimiter.of("kisApi", config);
        rateLimiter.getEventPublisher()
                .onFailure(event -> log.warn("KIS API rate limit 대기 실패: {}", event));

        log.info("KIS RateLimiter initialized - limit: {}/{}, timeout: {}ms",
                limitForPeriod, refreshPeriodMs, timeoutMs);
        return rateLimiter;
    }

    @Bean
    public Retry kisApiRetry(
            @Value("${kis.api.resilience.retry.max-attempts:3}") int maxAttempts,
            @Value("${kis.api.resilience.retry.initial-wait-ms:300}") long initialWaitMs,
            @Value("${kis.api.resilience.retry.multiplier:2.0}") double multiplier
    ) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(initialWaitMs, multiplier))
                .retryOnException(this::isRetryable)
                .build();

        Retry retry = Retry.of("kisApi", config);
        retry.getEventPublisher()
                .onRetry(event -> log.warn("KIS API 재시도: attempt={}, waitInterval={}, error={}",
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval(),
                        event.getLastThrowable().getMessage()))
                .onError(event -> log.error("KIS API 재시도 실패: attempts={}, error={}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        log.info("KIS Retry initialized - maxAttempts: {}, initialWait: {}ms, multiplier: {}",
                maxAttempts, initialWaitMs, multiplier);
        return retry;
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    || responseException.getStatusCode().value() == 429;
        }
        return false;
    }
}
