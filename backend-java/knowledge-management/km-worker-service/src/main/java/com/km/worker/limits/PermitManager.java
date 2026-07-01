package com.km.worker.limits;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * F4 整合（commit #24）：并发许可管理器（抽出独立文件，硬规则要求）。
 * 原为 WorkerApplication.java 单文件内嵌类。
 */
@Service
public class PermitManager {
    private static final String KEY = "km:processing:permits";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]); " +
                    "if redis.call('ZSCORE', KEYS[1], ARGV[3]) then " +
                    "  redis.call('ZADD', KEYS[1], ARGV[4], ARGV[3]); return 1; " +
                    "end; " +
                    "if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[2]) then " +
                    "  redis.call('ZADD', KEYS[1], ARGV[4], ARGV[3]); return 1; " +
                    "end; " +
                    "return 0;",
            Long.class);
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('ZSCORE', KEYS[1], ARGV[1]) then " +
                    "  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]); return 1; " +
                    "end; " +
                    "return 0;",
            Long.class);
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public PermitManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(Long taskId, String claimToken, int max) {
        long now = System.currentTimeMillis() / 1000;
        Long result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                Collections.singletonList(KEY),
                String.valueOf(now - 1),
                String.valueOf(Math.max(1, max)),
                member(taskId, claimToken),
                String.valueOf(now + 300));
        return result != null && result == 1L;
    }

    public boolean refresh(Long taskId, String claimToken) {
        String member = member(taskId, claimToken);
        Long result = redisTemplate.execute(
                REFRESH_SCRIPT,
                Collections.singletonList(KEY),
                member,
                String.valueOf(System.currentTimeMillis() / 1000 + 300));
        return result != null && result == 1L;
    }

    public void release(Long taskId, String claimToken) {
        redisTemplate.opsForZSet().remove(KEY, member(taskId, claimToken));
    }

    private String member(Long taskId, String claimToken) {
        return taskId + ":" + claimToken;
    }
}
