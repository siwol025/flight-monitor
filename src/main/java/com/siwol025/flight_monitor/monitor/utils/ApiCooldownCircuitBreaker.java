package com.siwol025.flight_monitor.monitor.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiCooldownCircuitBreaker {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalDateTime> localCooldownCache = new ConcurrentHashMap<>();

    private boolean isCircuitOpen = false;
    private long circuitOpenTime = 0;
    private static final long HALF_OPEN_TIMEOUT_MS = 60000;

    public boolean hasCooldown(String key) {
        if (shouldUseLocalFallback()) {
            return checkLocalCooldown(key);
        }

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            openCircuit(e);
            return checkLocalCooldown(key);
        }
    }

    public void setCooldown(String key, Duration ttl) {
        if (shouldUseLocalFallback()) {
            setLocalCooldown(key, ttl);
            return;
        }

        try {
            redisTemplate.opsForValue().set(key, "LOCKED", ttl);
        } catch (Exception e) {
            openCircuit(e);
            setLocalCooldown(key, ttl);
        }
    }

    private boolean shouldUseLocalFallback() {
        if (isCircuitOpen) {
            if (System.currentTimeMillis() - circuitOpenTime > HALF_OPEN_TIMEOUT_MS) {
                log.info("🔄 [Circuit Breaker] HALF-OPEN: Redis 재연결을 시도합니다.");
                isCircuitOpen = false;
                return false;
            }
            return true;
        }
        return false;
    }

    private void openCircuit(Exception e) {
        isCircuitOpen = true;
        circuitOpenTime = System.currentTimeMillis();
        log.error("🚨 [Circuit Breaker] OPEN: Redis 연결 실패! 인메모리 Fallback으로 전환합니다. (원인: {})", e.getMessage());
    }

    private boolean checkLocalCooldown(String key) {
        LocalDateTime expirationTime = localCooldownCache.get(key);
        if (expirationTime == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expirationTime)) {
            localCooldownCache.remove(key);
            return false;
        }

        return true;
    }

    private void setLocalCooldown(String key, Duration ttl) {
        LocalDateTime expirationTime = LocalDateTime.now().plus(ttl);
        localCooldownCache.put(key, expirationTime);
        log.warn("⚠️ [Fallback] 로컬 메모리에 쿨다운이 설정되었습니다. (Key: {})", key);
    }
}
