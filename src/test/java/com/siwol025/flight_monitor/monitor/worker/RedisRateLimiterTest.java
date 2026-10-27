package com.siwol025.flight_monitor.monitor.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 공유 레이트 리미터를 실제 Redis(compose와 같은 이미지)로 검사한다. 시간 기준이 Redis TIME이라 실제 시간으로 잰다.
 */
@Testcontainers
class RedisRateLimiterTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void 기간당N건_창안에서_N건까지허용() {
        // 1.5초당 3건 = 0.5초 간격. 버스트가 없어야 한 창에 N건을 크게 넘지 않는다
        RedisRateLimiter limiter = limiter(3, Duration.ofMillis(1500));
        limiter.acquire();
        long firstGrantNanos = System.nanoTime();

        int granted = 1;
        while (true) {
            limiter.acquire();
            if (elapsedMillis(firstGrantNanos) >= 1400) {
                break;
            }
            granted++;
        }

        assertThat(granted).isEqualTo(3);
    }

    @Test
    void 토큰없음_남은대기시간만큼대기_대기중Redis호출이폴링수준으로늘지않음() {
        // 2초당 1건: 첫 획득 뒤 다음 토큰까지 약 2초
        RedisRateLimiter limiter = limiter(1, Duration.ofSeconds(2));
        limiter.acquire();
        // 첫 호출의 스크립트 캐시 적재(EVALSHA 실패 → EVAL 재전송)는 세지 않는다
        resetCommandStats();
        long start = System.nanoTime();

        limiter.acquire();

        assertThat(elapsedMillis(start)).isBetween(1800L, 2500L);
        // 남은 시간만큼 한 번에 자므로 대기 중 Redis 호출은 몇 번뿐이다(10ms 재시도라면 약 200번)
        assertThat(scriptCalls()).isLessThanOrEqualTo(3);
    }

    @Test
    void 토큰키_보충간격전에만료되지않음() throws InterruptedException {
        // 3초당 1건: 보충 간격(3초)이 옛 토큰 키 만료(2초)보다 길다
        RedisRateLimiter limiter = limiter(1, Duration.ofSeconds(3));
        limiter.acquire();
        Thread.sleep(2500); // Redis를 부르지 않고 쉰다
        long start = System.nanoTime();

        limiter.acquire();

        // 키가 남아 있으면 남은 약 0.5초를 기다린다. 먼저 사라지면 꽉 찬 버킷을 새로 받아 즉시 통과한다
        // (Duffel 기본값에서는 실효 한도가 60초당 30건으로 올라 창 경계에서 429가 날 수 있다)
        assertThat(elapsedMillis(start)).isGreaterThanOrEqualTo(300L);
    }

    @Test
    void 전체정지기록_재개시각전_모든획득대기() {
        // 토큰은 넉넉하다(1초당 100건). 막는 것은 정지뿐이다
        RedisRateLimiter recorder = limiter(100, Duration.ofSeconds(1));
        RedisRateLimiter otherServer = limiter(100, Duration.ofSeconds(1)); // 같은 Redis를 쓰는 다른 서버
        // 기준 시각은 Redis TIME이다(Docker VM과 호스트 시계가 수백 ms 어긋날 수 있다)
        recorder.pauseUntil(redisNow().plusMillis(1500));
        long start = System.nanoTime();

        otherServer.acquire();

        // 재개 시각까지 기다리되 그보다 크게 늦지 않는다(남은 시간 대신 절대 시각을 자면 훨씬 길어진다)
        assertThat(elapsedMillis(start)).isBetween(1300L, 2000L);
    }

    @Test
    void 재개시각후_정상재개() throws InterruptedException {
        RedisRateLimiter limiter = limiter(100, Duration.ofSeconds(1));
        limiter.pauseUntil(redisNow().plusMillis(500));
        Thread.sleep(600);
        long start = System.nanoTime();

        limiter.acquire();

        assertThat(elapsedMillis(start)).isLessThan(200L);
    }

    @Test
    void 대기중인터럽트_AcquireInterruptedException으로중단() throws InterruptedException {
        // 60초당 1건: 토큰을 쓰고 나면 다음 획득은 약 60초를 잔다
        RedisRateLimiter limiter = limiter(1, Duration.ofSeconds(60));
        limiter.acquire();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedFlag = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                limiter.acquire();
            } catch (Throwable e) {
                thrown.set(e);
                interruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        Thread.sleep(300); // 수면에 들어갈 시간

        waiter.interrupt();
        waiter.join(500);

        // 폴러는 이 예외를 받아 permit을 반납하고 끝난다(InFlightBackpressure, FlightMonitoringWorker)
        assertThat(waiter.isAlive()).isFalse();
        assertThat(thrown.get()).isInstanceOf(AcquireInterruptedException.class);
        assertThat(interruptedFlag.get()).isTrue();
    }

    private static Instant redisNow() {
        Long epochMillis = redis.execute((RedisCallback<Long>) connection -> connection.serverCommands().time());
        return Instant.ofEpochMilli(epochMillis);
    }

    private static void resetCommandStats() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().resetConfigStats();
            return null;
        });
    }

    private static long scriptCalls() {
        Properties stats = redis.execute((RedisCallback<Properties>) connection ->
                connection.serverCommands().info("commandstats"));
        return calls(stats, "cmdstat_evalsha") + calls(stats, "cmdstat_eval");
    }

    // 값 형식: calls=3,usec=120,usec_per_call=40.00,rejected_calls=0,failed_calls=0
    private static long calls(Properties stats, String command) {
        String value = stats.getProperty(command);
        if (value == null) {
            return 0;
        }
        return Long.parseLong(value.split(",")[0].substring("calls=".length()));
    }

    private static RedisRateLimiter limiter(int limit, Duration period) {
        return new RedisRateLimiter(redis, new RateLimiterProperties(limit, period));
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
