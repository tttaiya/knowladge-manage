package com.km.worker.limits;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * F4 整合（commit #24）：并发许可管理器（抽出独立文件，硬规则要求）。
 * 原为 WorkerApplication.java 单文件内嵌类。
 */
@Service
public class PermitManager {
    private static final String KEY = "km:processing:permits";
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public PermitManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(Long taskId, String claimToken, int max) {
        long now = System.currentTimeMillis() / 1000;
        redisTemplate.opsForZSet().removeRangeByScore(KEY, 0, now - 1);
        Long size = redisTemplate.opsForZSet().zCard(KEY);
        if (size != null && size < max) {
            redisTemplate.opsForZSet().add(KEY, member(taskId, claimToken), now + 300);
            return true;
        }
        return false;
    }

    public boolean refresh(Long taskId, String claimToken) {
        String member = member(taskId, claimToken);
        Double score = redisTemplate.opsForZSet().score(KEY, member);
        if (score == null) {
            return false;
        }
        redisTemplate.opsForZSet().add(KEY, member, System.currentTimeMillis() / 1000 + 300);
        return true;
    }

    public void release(Long taskId, String claimToken) {
        redisTemplate.opsForZSet().remove(KEY, member(taskId, claimToken));
    }

    private String member(Long taskId, String claimToken) {
        return taskId + ":" + claimToken;
    }
}