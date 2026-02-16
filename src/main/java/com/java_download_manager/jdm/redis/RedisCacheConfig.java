package com.java_download_manager.jdm.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Application caches that use Redis when available: registers beans for taken account names and
 * token public keys. Each cache has a real implementation when {@link RedisTemplate} exists and
 * a no-op when Redis is not configured.
 */
@Configuration
public class RedisCacheConfig {

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public TakenAccountNameCache takenAccountNameCache(RedisTemplate<String, String> redisTemplate) {
        return new TakenAccountNameCache(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TakenAccountNameCache.class)
    public TakenAccountNameCache noOpTakenAccountNameCache() {
        return new TakenAccountNameCache(null);
    }

    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    public TokenPublicKeyCache tokenPublicKeyCache(RedisTemplate<String, String> redisTemplate) {
        return new TokenPublicKeyCache(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TokenPublicKeyCache.class)
    public TokenPublicKeyCache noOpTokenPublicKeyCache() {
        return new TokenPublicKeyCache(null);
    }
}
