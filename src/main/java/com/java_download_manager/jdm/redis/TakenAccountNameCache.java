package com.java_download_manager.jdm.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

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
