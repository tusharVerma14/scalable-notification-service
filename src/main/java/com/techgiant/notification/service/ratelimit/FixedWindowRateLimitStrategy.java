package com.techgiant.notification.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed window rate limiting.
 *
 * Each API key gets a counter that resets every 60 seconds.
 * If the counter exceeds the limit, requests return 429.
 *
 * Simpler than token bucket but has an edge case: if the limit is 1000/min,
 * a caller can send 1000 at the last second of one window and 1000 at the
 * first second of the next — effectively 2000 in 2 seconds.
 * Use token bucket for production to avoid this.
 */
@Component("fixedWindow")
@RequiredArgsConstructor
@Slf4j
public class FixedWindowRateLimitStrategy implements RateLimitStrategy {

    private static final long MAX_REQUESTS = 1000L;
    private static final String KEY_PREFIX  = "fw_rate_limit:";
    private static final Duration WINDOW    = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean isAllowed(String apiKey) {
        try {
            long windowStart = System.currentTimeMillis() / WINDOW.toMillis();
            String key = KEY_PREFIX + apiKey + ":" + windowStart;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, WINDOW);
            }

            if (count > MAX_REQUESTS) {
                log.warn("[FixedWindow] Rate limit hit for key '{}'", apiKey);
                return false;
            }
            return true;

        } catch (Exception e) {
            // fail open if Redis is unavailable
            log.warn("[FixedWindow] Redis unavailable for key '{}', failing open: {}", apiKey, e.getMessage());
            return true;
        }
    }

    @Override
    public String getStrategyName() {
        return "FixedWindow";
    }
}
