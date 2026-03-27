package com.techgiant.notification.service.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Token bucket rate limiting.
 *
 * Each API key gets a bucket of 1000 tokens. Each request consumes one token.
 * Tokens refill at ~16.7/second (1000 per 60 seconds).
 * When the bucket is empty, requests return 429.
 *
 * Redis stores per key (hash):
 *   tokens         - current count
 *   last_refill_ms - timestamp of last request, used to calculate how many tokens to add
 */
@Primary
@Component("tokenBucket")
public class TokenBucketRateLimitStrategy implements RateLimitStrategy {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimitStrategy.class);

    private static final long MAX_TOKENS = 1000L;
    private static final long WINDOW_MS  = 60_000L;
    private static final String KEY_PREFIX = "tb_rate_limit:";

    private final RedisTemplate<String, String> redisTemplate;

    public TokenBucketRateLimitStrategy(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isAllowed(String apiKey) {
        try {
            String key = KEY_PREFIX + apiKey;
            long now = System.currentTimeMillis();

            Map<Object, Object> bucket = redisTemplate.opsForHash().entries(key);

            long currentTokens;
            long lastRefillMs;

            if (bucket.isEmpty()) {
                currentTokens = MAX_TOKENS;
                lastRefillMs  = now;
            } else {
                currentTokens = Long.parseLong(bucket.get("tokens").toString());
                lastRefillMs  = Long.parseLong(bucket.get("last_refill_ms").toString());
            }

            // add tokens based on elapsed time since last request
            long elapsedMs   = now - lastRefillMs;
            long tokensToAdd = (elapsedMs * MAX_TOKENS) / WINDOW_MS;
            currentTokens    = Math.min(MAX_TOKENS, currentTokens + tokensToAdd);

            if (currentTokens <= 0) {
                saveState(key, 0L, now);
                log.warn("[TokenBucket] Rate limit hit for key '{}'", apiKey);
                return false;
            }

            saveState(key, currentTokens - 1, now);
            return true;

        } catch (Exception e) {
            // if Redis is down, fail open so we don't block all traffic
            log.warn("[TokenBucket] Redis unavailable for key '{}', failing open: {}", apiKey, e.getMessage());
            return true;
        }
    }

    private void saveState(String key, long tokens, long timestampMs) {
        redisTemplate.opsForHash().putAll(key, Map.of(
            "tokens",         String.valueOf(tokens),
            "last_refill_ms", String.valueOf(timestampMs)
        ));
        redisTemplate.expire(key, Duration.ofMinutes(2));
    }

    @Override
    public String getStrategyName() {
        return "TokenBucket";
    }
}
