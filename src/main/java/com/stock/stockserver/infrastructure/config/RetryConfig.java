package com.stock.stockserver.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableRetry
@Slf4j
public class RetryConfig {

    @Bean
    public RetryTemplate llmApiRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 재시도 정책: 최대 2회 재시도
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3); // 초기 포함 3회

        // 백오프 정책: 2초 -> 4초 -> 8초
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000); // 2초
        backOffPolicy.setMultiplier(2.0); // 2배씩 증가
        backOffPolicy.setMaxInterval(8000); // 최대 8초

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        log.info("LLM API Retry Template initialized with max attempts: 3, backoff: 2s->4s->8s");
        return retryTemplate;
    }
}