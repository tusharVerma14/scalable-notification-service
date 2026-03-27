package com.techgiant.notification.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SessionManagementService {

    private final RedisTemplate<String, String> stringRedisTemplate;
    private static final String SESSION_PREFIX = "user_sessions:";

    public SessionManagementService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // called on WebSocket CONNECT
    public void addSession(String userId, String sessionId) {
        String key = SESSION_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(key, sessionId);
        // 24hr TTL prevents stale sessions if disconnect event is missed (e.g. server crash)
        stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    // called on WebSocket DISCONNECT
    public void removeSession(String userId, String sessionId) {
        String key = SESSION_PREFIX + userId;
        stringRedisTemplate.opsForSet().remove(key, sessionId);
    }

    public Set<String> getActiveSessions(String userId) {
        String key = SESSION_PREFIX + userId;
        return stringRedisTemplate.opsForSet().members(key);
    }

    public boolean isUserOffline(String userId) {
        Set<String> sessions = getActiveSessions(userId);
        return sessions == null || sessions.isEmpty();
    }
}
