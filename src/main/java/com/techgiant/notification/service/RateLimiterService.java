package com.techgiant.notification.service;

import com.techgiant.notification.service.ratelimit.RateLimitStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    // Spring injects whichever strategy has @Primary (currently TokenBucket)
    private final RateLimitStrategy rateLimitStrategy;

    @PostConstruct
    public void logActiveStrategy() {
        log.info("Rate limiting active strategy: [{}]", rateLimitStrategy.getStrategyName());
    }

    public boolean isAllowed(String apiKey) {
        return rateLimitStrategy.isAllowed(apiKey);
    }
}
