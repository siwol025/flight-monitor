package com.siwol025.flight_monitor.monitor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * rate 단계(공유 레이트 리미터)가 실패해도 이미 얻은 permit을 돌려주는지 실제 리미터(실제 Redis)로 검사한다.
 * permit이 새면 maxInFlight 1에서 다음 획득이 세마포어에 영원히 막힌다.
 */
@Testcontainers
class InFlightBackpressureTest {

    private static final String TOKEN_KEY = "monitor:ratelimiter:tokens";

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
        flushAll();
    }

    @Test
    void rate단계Redis예외_permit반납_가용permit수보존() throws InterruptedException {
        InFlightBackpressure backpressure = backpressure(new RedisRateLimiter(redis,
                new RateLimiterProperties(1, Duration.ofSeconds(60), 1),
                RateLimiterCircuits.likeProduction(Duration.ofSeconds(10))));
        // 토큰 키가 다른 타입이면 Lua가 WRONGTYPE으로 실패한다. 연결 장애가 아니라서 그대로 위로 전파되는 Redis 예외다
        redis.opsForValue().set(TOKEN_KEY, "broken");
        assertThatThrownBy(backpressure::acquire).isInstanceOf(RedisSystemException.class);
        redis.delete(TOKEN_KEY);

        assertThat(acquiresWithin(backpressure, Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void rate단계인터럽트_permit반납_가용permit수보존() throws InterruptedException {
        RedisRateLimiter limiter = new RedisRateLimiter(redis, new RateLimiterProperties(1, Duration.ofSeconds(60), 1),
                RateLimiterCircuits.likeProduction(Duration.ofSeconds(10)));
        InFlightBackpressure backpressure = backpressure(limiter);
        // 공유 토큰을 미리 써서 다음 획득이 rate 단계에서 약 60초를 기다리게 한다
        limiter.acquire();
        assertThat(acquiresWithin(backpressure, Duration.ofMillis(500))).isFalse();
        // 기다리다 인터럽트로 끝났다. 새 버킷으로 토큰을 다시 채운다
        flushAll();

        assertThat(acquiresWithin(backpressure, Duration.ofSeconds(1))).isTrue();
    }

    private static InFlightBackpressure backpressure(RedisRateLimiter limiter) {
        return new InFlightBackpressure(1,
                new StaticListableBeanFactory(Map.of("rateLimiter", limiter)).getBeanProvider(RedisRateLimiter.class));
    }

    /** 다른 스레드에서 획득을 시도해 시간 안에 끝났는지 본다. 막혀 있으면 인터럽트로 풀어 준다 */
    private static boolean acquiresWithin(InFlightBackpressure backpressure, Duration timeout)
            throws InterruptedException {
        CountDownLatch acquired = new CountDownLatch(1);
        Thread acquirer = new Thread(() -> {
            try {
                backpressure.acquire();
                acquired.countDown();
            } catch (AcquireInterruptedException ignored) {
                // 시간 안에 못 얻어 테스트가 인터럽트했다
            }
        });
        acquirer.start();
        boolean done = acquired.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        acquirer.interrupt();
        acquirer.join(1000);
        return done;
    }

    private static void flushAll() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
