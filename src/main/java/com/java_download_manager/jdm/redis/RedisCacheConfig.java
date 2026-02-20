package com.java_download_manager.jdm.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisCacheConfig {

    @Bean
    @ConditionalOnBean(name = "jdmRedisTemplate")
    public TakenAccountNameCache takenAccountNameCache(
            @Qualifier("jdmRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        return new TakenAccountNameCache(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TakenAccountNameCache.class)
    public TakenAccountNameCache noOpTakenAccountNameCache() {
        return new TakenAccountNameCache(null);
    }

    @Bean
    @ConditionalOnBean(name = "jdmRedisTemplate")
    public TokenPublicKeyCache tokenPublicKeyCache(
            @Qualifier("jdmRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        return new TokenPublicKeyCache(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TokenPublicKeyCache.class)
    public TokenPublicKeyCache noOpTokenPublicKeyCache() {
        return new TokenPublicKeyCache(null);
    }

    @Bean
    @ConditionalOnBean(name = "jdmRedisTemplate")
    public TakenEmailCache takenEmailCache(
            @Qualifier("jdmRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        return new TakenEmailCache(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TakenEmailCache.class)
    public TakenEmailCache noOpTakenEmailCache() {
        return new TakenEmailCache(null);
    }
}
