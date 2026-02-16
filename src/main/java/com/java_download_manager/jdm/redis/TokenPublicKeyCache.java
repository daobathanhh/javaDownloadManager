package com.java_download_manager.jdm.redis;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cache of JWT token public keys by kid. Used for verification without hitting DB.
 * When Redis is not configured, a no-op instance is provided (get returns empty, put no-ops).
 */
public class TokenPublicKeyCache {

    private static final String REDIS_KEY_PREFIX = "jdm:token_public_key:";
    private static final long TTL_DAYS = 7;

    private final RedisTemplate<String, String> redisTemplate;

    public TokenPublicKeyCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> get(String kid) {
        if (redisTemplate == null || kid == null) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + kid);
        return Optional.ofNullable(value);
    }

    public void put(String kid, String publicKeyPem) {
        if (redisTemplate == null || kid == null || publicKeyPem == null) {
            return;
        }
        redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + kid, publicKeyPem, TTL_DAYS, TimeUnit.DAYS);
    }
}
