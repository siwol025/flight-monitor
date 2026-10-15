package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 분산 rate limiter (토큰 버킷, Lua 원자 실행).
 *
 * <p>멀티 인스턴스 환경에서 외부 API 전역 rate 한도를 <b>공유 토큰 버킷</b>으로 강제한다.
 * 각 인스턴스가 로컬로 한도를 걸면 합산이 한도를 초과하므로(N 인스턴스 × 로컬 한도), 토큰을 Redis에 두고
 * 모든 인스턴스·워커가 공유한다. Lua 스크립트가 Redis 단일 스레드에서 원자 실행되므로 동시 pop 시에도
 * 이중 차감 없이 전역 한도가 보장된다. pop 직전 {@link #acquire()}로 토큰을 얻을 때까지 블로킹.</p>
 *
 * <p>{@code monitor.ratelimiter.enabled=true} 일 때만 활성화된다.</p>
 */
@Component
@Profile("!mock")
@ConditionalOnProperty(name = "monitor.ratelimiter.enabled", havingValue = "true")
public class RedisRateLimiter {

    private static final String KEY = "monitor:ratelimiter:tokens";
    private static final String LUA = """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = capacity; ts = now end
            local elapsed = now - ts
            if elapsed < 0 then elapsed = 0 end
            tokens = math.min(capacity, tokens + elapsed * rate / 1000.0)
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('HMSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', key, 2000)
            return allowed
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final int ratePerSec;

    public RedisRateLimiter(StringRedisTemplate redis,
                            @Value("${monitor.ratelimiter.rate-per-sec:100}") int ratePerSec) {
        this.redis = redis;
        this.ratePerSec = ratePerSec;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /** 토큰을 얻을 때까지 블로킹(전역 rate 한도 준수). */
    public void acquire() {
        while (true) {
            Long allowed = redis.execute(script, List.of(KEY),
                    String.valueOf(ratePerSec), String.valueOf(ratePerSec),
                    String.valueOf(System.currentTimeMillis()));
            if (allowed != null && allowed == 1L) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AcquireInterruptedException(e);
            }
        }
    }
}
