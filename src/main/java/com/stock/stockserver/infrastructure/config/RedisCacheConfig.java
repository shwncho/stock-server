package com.stock.stockserver.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String LLM_CACHE = "llmAnalysisCache";
    public static final String KIS_DAILY_CACHE = "kisDailyCache";
    public static final String KIS_VOLUME_RANK_CACHE = "kisVolumeRankCache";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
                )
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(5)); // default TTL

        RedisCacheConfiguration llmConfig = defaultConfig.entryTtl(Duration.ofHours(6));
        RedisCacheConfiguration kisDailyConfig = defaultConfig.entryTtl(Duration.ofHours(1));
        RedisCacheConfiguration kisVolumeRankConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(LLM_CACHE, llmConfig)
                .withCacheConfiguration(KIS_DAILY_CACHE, kisDailyConfig)
                .withCacheConfiguration(KIS_VOLUME_RANK_CACHE, kisVolumeRankConfig)
                .build();
    }
}