package com.java_download_manager.jdm.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

/**
 * Cache of taken emails to avoid DB hits on duplicate-email checks.
 * When Redis is not configured, a bean is still provided but all operations no-op.
 */
public class TakenEmailCache {

    private static final String REDIS_KEY = "jdm:taken_email_set";

    private final RedisTemplate<String, String> redisTemplate;

    public TakenEmailCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isTaken(String email) {
        if (redisTemplate == null || email == null) {
            return false;
        }
        SetOperations<String, String> set = redisTemplate.opsForSet();
        return Boolean.TRUE.equals(set.isMember(REDIS_KEY, email));
    }

    public void add(String email) {
        if (redisTemplate == null || email == null) {
            return;
        }
        redisTemplate.opsForSet().add(REDIS_KEY, email);
    }

    public void remove(String email) {
        if (redisTemplate == null || email == null) {
            return;
        }
        redisTemplate.opsForSet().remove(REDIS_KEY, email);
    }
}
