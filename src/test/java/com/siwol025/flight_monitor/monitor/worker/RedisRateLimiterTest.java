package com.siwol025.flight_monitor.monitor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
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
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
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

    // 장애 테스트의 OPEN 유지 시간(운영은 10초)
    private static final Duration OPEN_WAIT = Duration.ofMillis(500);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    // 장애 테스트용 연결: 앱처럼 명령 타임아웃을 모든 명령에 걸되, 짧게(200ms) 둬서 테스트가 느려지지 않게 한다
    private static LettuceConnectionFactory outageConnectionFactory;
    private static StringRedisTemplate outageRedis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        outageConnectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)),
                LettuceClientConfiguration.builder()
                        .clientOptions(ClientOptions.builder().timeoutOptions(TimeoutOptions.enabled()).build())
                        .commandTimeout(Duration.ofMillis(200))
                        .build());
        outageConnectionFactory.afterPropertiesSet();
        outageConnectionFactory.start();
        outageRedis = new StringRedisTemplate(outageConnectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
        outageConnectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        flushAll();
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

    @Test
    void Redis장애_서킷열림_로컬버킷으로전환_로컬한도준수() {
        // 공유 1초당 10건, 서버 5대 → Redis 장애 중 이 서버는 1초당 2건(0.5초 간격)으로 따로 센다
        CircuitBreakerRegistry circuits = RateLimiterCircuits.likeProduction(OPEN_WAIT);
        RedisRateLimiter limiter = outageLimiter(10, Duration.ofSeconds(1), 5, circuits);
        // 장애 전에 연결을 맺어 둔다(이미 맺은 연결에서 Redis가 응답을 멈추는 상황 → 명령 시간 초과)
        limiter.acquire();
        int granted = 0;
        pauseRedis();
        try {
            limiter.acquire();
            long start = System.nanoTime();
            while (elapsedMillis(start) < 2000) {
                limiter.acquire();
                granted++;
            }
        } finally {
            unpauseRedis();
        }

        // 2초에 로컬 간격(0.5초)대로 약 4건. 멈추지 않고 내주되(2건 이상) 공유 한도(약 20건)로 내주지 않는다
        assertThat(granted).isBetween(2, 5);
        assertThat(circuits.circuitBreaker(RedisRateLimiter.CIRCUIT_BREAKER_NAME).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void Redis복구_HALFOPEN확인후_공유버킷으로복귀() throws InterruptedException {
        // 공유 1초당 1건(서버 1대라 장애 중 로컬도 1초당 1건)
        CircuitBreakerRegistry circuits = RateLimiterCircuits.likeProduction(OPEN_WAIT);
        CircuitBreaker circuit = circuits.circuitBreaker(RedisRateLimiter.CIRCUIT_BREAKER_NAME);
        RedisRateLimiter server = outageLimiter(1, Duration.ofSeconds(1), 1, circuits);
        server.acquire();
        pauseRedis();
        try {
            server.acquire();
            server.acquire();
        } finally {
            unpauseRedis();
        }
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        // 장애 중 시간 초과로 끝난 eval도 재개 뒤 실제로 실행돼 공유 토큰을 쓴다. 그 흔적을 지우고 잰다
        Thread.sleep(200);
        flushAll();
        Thread.sleep(OPEN_WAIT.toMillis());

        // OPEN 유지 시간이 지나면 다음 획득 1건이 HALF_OPEN에서 Redis를 확인하고, 성공하면 서킷이 닫힌다
        server.acquire();
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 그 획득이 공유 버킷의 토큰을 썼으므로 같은 Redis를 쓰는 다른 서버는 다음 토큰(약 1초)을 기다린다.
        // 아직 로컬로 셌다면 공유 버킷이 가득 차 있어 바로 받는다
        RedisRateLimiter otherServer = limiter(1, Duration.ofSeconds(1));
        long start = System.nanoTime();
        otherServer.acquire();
        assertThat(elapsedMillis(start)).isGreaterThanOrEqualTo(700L);
    }

    @Test
    void Redis장애중429_정지는로컬기록_복구후에도재개시각까지대기() throws InterruptedException {
        RedisRateLimiter server = outageLimiter(100, Duration.ofSeconds(1), 1,
                RateLimiterCircuits.likeProduction(OPEN_WAIT));
        server.acquire();
        Instant resumeAt;
        pauseRedis();
        try {
            // 처리기가 받은 429의 재개 시각을 기록한다. Redis에 쓰지 못해도 예외가 처리기로 새지 않는다
            resumeAt = Instant.now().plusMillis(1500);
            assertThatCode(() -> server.pauseUntil(resumeAt)).doesNotThrowAnyException();
        } finally {
            unpauseRedis();
        }
        // 장애 중 시간 초과로 끝난 SET도 재개 뒤 실행돼 Redis에 정지 키가 생긴다. 지워서 이 서버의 로컬 기록만 남긴다
        Thread.sleep(200);
        flushAll();

        server.acquire();

        // Redis는 복구됐고 정지 기록도 없지만, 이 서버는 로컬로 기록한 재개 시각(JVM 시계)까지 기다렸다
        assertThat(Instant.now()).isAfterOrEqualTo(resumeAt.minusMillis(5));
    }

    private static RedisRateLimiter outageLimiter(int limit, Duration period, int instances,
                                                  CircuitBreakerRegistry circuits) {
        return new RedisRateLimiter(outageRedis, new RateLimiterProperties(limit, period, instances), circuits);
    }

    // 컨테이너를 멈췄다 다시 켜면 매핑 포트가 바뀐다. 일시정지는 주소를 유지한 채 응답만 멈춘다(명령은 시간 초과)
    private static void pauseRedis() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    }

    private static void unpauseRedis() {
        REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }

    private static void flushAll() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
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
        return new RedisRateLimiter(redis, new RateLimiterProperties(limit, period, 1),
                RateLimiterCircuits.likeProduction(Duration.ofSeconds(10)));
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
