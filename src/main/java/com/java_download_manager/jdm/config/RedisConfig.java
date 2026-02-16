package com.java_download_manager.jdm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure: creates {@link org.springframework.data.redis.core.RedisTemplate} only when
 * spring.redis.host is set. Application caches (taken account names, token public keys, etc.) are
 * defined in the redis package and use this template when available.
 */
@Configuration
@ConditionalOnProperty(name = "spring.redis.host")
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
