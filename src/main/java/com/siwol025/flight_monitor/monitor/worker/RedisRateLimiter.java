package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 분산 rate limiter (기간당 N건 토큰 버킷 + 429 전체 정지, Lua 원자 실행).
 *
 * <p>멀티 인스턴스 환경에서 외부 API 전역 rate 한도를 <b>공유 토큰 버킷</b>으로 강제한다.
 * 각 인스턴스가 로컬로 한도를 걸면 합산이 한도를 초과하므로(N 인스턴스 × 로컬 한도), 토큰을 Redis에 두고
 * 모든 인스턴스·워커가 공유한다. Lua 스크립트가 Redis 단일 스레드에서 원자 실행되므로 동시 pop 시에도
 * 이중 차감 없이 전역 한도가 보장된다. pop 직전 {@link #acquire()}로 토큰을 얻을 때까지 블로킹.</p>
 *
 * <p>Redis가 응답하지 않거나 연결되지 않으면 서킷({@value #CIRCUIT_BREAKER_NAME})이 열리고, 그동안은 서버마다
 * 로컬 버킷(전역 한도 ÷ 서버 대수)으로 센다. 서킷이 HALF_OPEN에서 Redis 복구를 확인하면 공유 버킷으로 돌아간다.</p>
 *
 * <p>{@code monitor.ratelimiter.enabled=true} 일 때만 활성화된다.</p>
 */
@Slf4j
@Component
@Profile("!mock")
@ConditionalOnProperty(name = "monitor.ratelimiter.enabled", havingValue = "true")
// 설정은 리미터가 켜진 경우에만 등록한다(테스트 yml처럼 설정이 없으면 limit 0 검사로 기동이 실패하므로)
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RedisRateLimiter {

    // resilience4j.circuitbreaker.instances에 같은 이름으로 설정한다(실패로 셀 예외, 열림 조건)
    static final String CIRCUIT_BREAKER_NAME = "redisRateLimiterCircuitBreaker";
    private static final String KEY = "monitor:ratelimiter:tokens";
    private static final String PAUSE_KEY = "monitor:ratelimiter:pause";
    // KEYS[1] 토큰 상태, KEYS[2] 전체 정지(재개 시각 epoch ms). ARGV[1] 기간당 건수, ARGV[2] 기간(ms).
    // 반환: 획득하면 0, 아니면 다음 토큰 또는 재개 시각까지 남은 ms. 호출자는 그만큼 잔다(짧은 주기 재시도 없음).
    // 시각은 Redis TIME으로 잰다(인스턴스마다 다른 JVM 시계를 섞지 않는다).
    // 용량 1: 버스트 없이 기간 ÷ N 간격으로 고르게 낸다(용량 N이면 60초 고정 창 하나에 최대 2N건).
    private static final String LUA = """
            local limit = tonumber(ARGV[1])
            local periodMs = tonumber(ARGV[2])
            local interval = periodMs / limit
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            -- 전체 정지(429) 확인과 토큰 획득을 한 스크립트에서 해, 그 사이에 끼어드는 획득이 없다
            local resumeAt = tonumber(redis.call('GET', KEYS[2]))
            if resumeAt and resumeAt > now then
                return math.max(1, math.ceil(resumeAt - now))
            end
            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = 1; ts = now end
            local elapsed = now - ts
            if elapsed < 0 then elapsed = 0 end
            tokens = math.min(1, tokens + elapsed / interval)
            local wait = 0
            if tokens >= 1 then
                tokens = tokens - 1
            else
                -- EVAL은 Lua 실수를 정수 응답으로 바꿀 때 소수점을 버린다. 0.4ms 같은 값이 0(= 획득)으로 읽혀
                -- 토큰 없이 통과하지 않게 올림하고 최소 1을 돌려준다
                wait = math.max(1, math.ceil((1 - tokens) * interval))
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
            -- 만료는 보충 간격(기간 ÷ N) 이상이어야 한다. 쉬는 동안 키가 먼저 사라지면 꽉 찬 버킷을 새로 받아
            -- 실효 한도가 올라간다. 기간으로 두면 항상 간격 이상이다
            redis.call('PEXPIRE', KEYS[1], periodMs)
            return wait
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final RateLimiterProperties properties;
    // Redis 호출(eval·SET)만 감싼다. 대기 수면까지 감싸면 긴 대기가 느린 호출로, 인터럽트가 실패로 세진다
    private final CircuitBreaker circuitBreaker;
    // Redis 장애 중 이 서버만의 버킷(용량 1, 공유 버킷과 같은 계약). 간격 = 기간 × 서버 대수 ÷ 한도.
    // 서버당 한도를 정수로 버리지 않는다(서버 합계가 전역 한도와 같고, 대수가 한도보다 많아도 0건이 되지 않는다)
    private final long localIntervalNanos;
    private long localNextFreeNanos;
    // 이 서버가 기록한 429 재개 시각(epoch ms, JVM 시계). 단순 덮어쓰기(공유 정지와 같은 규칙)
    private volatile long localResumeAtMillis;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimiterProperties properties,
                            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redis = redis;
        this.properties = properties;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        // 호출마다가 아니라 전환 때만 남긴다(장애 중 로그 폭주 방지)
        this.circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "[RedisRateLimiter] 공유 레이트 리미터 서킷 {} (OPEN이면 Redis 대신 서버별 로컬 버킷으로 센다)",
                event.getStateTransition()));
        this.localIntervalNanos = Math.round(
                properties.period().toNanos() * (double) properties.instances() / properties.limit());
        this.localNextFreeNanos = System.nanoTime();
    }

    /**
     * 업체가 호출 한도 초과(429)로 알려 준 재개 시각까지 모든 인스턴스의 획득을 멈춘다. 업체 한도는 API 키 단위라
     * 정지도 Redis에서 공유한다. 재개 시각을 절대 만료(PXAT)로 기록하므로 키가 그때 스스로 사라진다.
     *
     * <p>이 서버는 Redis와 상관없이 재개 시각을 로컬(JVM 시계)에도 먼저 기록해 지킨다. Redis 장애로 공유 기록에
     * 실패해도 예외를 처리기로 넘기지 않고, 공유 버킷으로 돌아간 뒤에도 재개 시각까지 멈춘다.</p>
     */
    public void pauseUntil(Instant resumeAt) {
        localResumeAtMillis = resumeAt.toEpochMilli();
        byte[] key = PAUSE_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] value = String.valueOf(resumeAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8);
        try {
            circuitBreaker.executeRunnable(() -> redis.execute((RedisCallback<Boolean>) connection ->
                    connection.stringCommands().set(key, value,
                            Expiration.unixTimestamp(resumeAt.toEpochMilli(), TimeUnit.MILLISECONDS),
                            SetOption.upsert())));
        } catch (DataAccessException | CallNotPermittedException e) {
            log.warn("[RedisRateLimiter] 전체 정지를 Redis에 기록하지 못해 이 서버만 {}까지 멈춥니다. 원인: {}",
                    resumeAt, e.getMessage());
        }
    }

    /** 토큰을 얻을 때까지 블로킹(전역 rate 한도 준수). 토큰이 없으면 다음 토큰까지 남은 시간만큼 잔다. */
    public void acquire() {
        while (true) {
            // 로컬 정지를 먼저 본다(공유 정지는 Lua가 본다)
            long waitMillis = Math.max(0, localResumeAtMillis - System.currentTimeMillis());
            if (waitMillis == 0L) {
                waitMillis = tryAcquire();
            }
            if (waitMillis == 0L) {
                return;
            }
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AcquireInterruptedException(e);
            }
        }
    }

    /**
     * 공유 버킷(Redis)에서 획득을 시도한다. Redis가 응답하지 않거나 연결되지 않으면(서킷이 실패로 세는 예외, 열린 서킷)
     * 이 서버의 로컬 버킷으로 센다. 그 밖의 Redis 오류(WRONGTYPE 등)는 폴백 뒤로 숨기지 않고 그대로 던진다.
     *
     * @return 획득하면 0, 아니면 다음 토큰 또는 재개 시각까지 남은 ms
     */
    private long tryAcquire() {
        try {
            return circuitBreaker.executeSupplier(() -> redis.execute(script, List.of(KEY, PAUSE_KEY),
                    String.valueOf(properties.limit()), String.valueOf(properties.period().toMillis())));
        } catch (CallNotPermittedException e) {
            return tryAcquireLocally();
        } catch (RuntimeException e) {
            // 실패로 셀 예외는 서킷 설정(yml) 한 곳에서 정한다
            if (circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate().test(e)) {
                return tryAcquireLocally();
            }
            throw e;
        }
    }

    /**
     * 로컬 버킷. 공유 Lua와 같은 계약이다: 획득하면 0, 아니면 다음 토큰까지 남은 ms(올림, 최소 1).
     */
    private synchronized long tryAcquireLocally() {
        long now = System.nanoTime();
        long waitNanos = localNextFreeNanos - now;
        if (waitNanos <= 0) {
            // 용량 1: 쉬던 동안의 토큰을 쌓지 않고 다음 토큰을 지금부터 한 간격 뒤로 둔다
            localNextFreeNanos = now + localIntervalNanos;
            return 0;
        }
        return Math.max(1, (waitNanos + 999_999) / 1_000_000);
    }
}
