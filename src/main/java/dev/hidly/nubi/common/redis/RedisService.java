package dev.hidly.nubi.common.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> values;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.values = redisTemplate.opsForValue();
    }

    public void setValues(String key, String data) {
        values.set(key, data);
    }

    public void setValues(String key, String data, Duration duration) {
        values.set(key, data, duration);
    }

    public Object getValues(String key) {
        return values.get(key);
    }

    public void deleteValues(String key) {
        redisTemplate.delete(key);
    }

    public long increment(String key, Duration ttl) {
        Long count = values.increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }

    public Object getAndDelete(String key) {
        return values.getAndDelete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
