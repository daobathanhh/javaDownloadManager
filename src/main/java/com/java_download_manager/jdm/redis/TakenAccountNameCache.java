package com.java_download_manager.jdm.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

/**
 * Cache of taken account names to avoid DB hits on duplicate-name checks.
 * When Redis is not configured, a bean is still provided but all operations no-op.
 */
public class TakenAccountNameCache {

    private static final String REDIS_KEY = "jdm:taken_account_name_set";

    private final RedisTemplate<String, String> redisTemplate;

    public TakenAccountNameCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isTaken(String accountName) {
        if (redisTemplate == null || accountName == null) {
            return false;
        }
        SetOperations<String, String> set = redisTemplate.opsForSet();
        return Boolean.TRUE.equals(set.isMember(REDIS_KEY, accountName));
    }

    public void add(String accountName) {
        if (redisTemplate == null || accountName == null) {
            return;
        }
        redisTemplate.opsForSet().add(REDIS_KEY, accountName);
    }

    public void remove(String accountName) {
        if (redisTemplate == null || accountName == null) {
            return;
        }
        redisTemplate.opsForSet().remove(REDIS_KEY, accountName);
    }
}
