package com.techgiant.notification.service.ratelimit;

public interface RateLimitStrategy {

    // returns true if request is allowed, false if rate limit exceeded
    boolean isAllowed(String apiKey);

    String getStrategyName();
}
