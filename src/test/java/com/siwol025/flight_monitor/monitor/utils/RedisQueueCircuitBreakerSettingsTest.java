package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/**
 * 메인 application.yml의 Redis 큐 서킷 설정. 테스트 클래스패스의 application.yml은 테스트 것이라 메인 파일을 경로로 읽는다.
 */
class RedisQueueCircuitBreakerSettingsTest {

    private static final String PUSH = "redisPushCircuitBreaker";
    private static final String POP = "redisPopCircuitBreaker";
    private static final String QUEUE_SIZE = "redisQueueSizeCircuitBreaker";

    @Test
    void 쓰기서킷_한번실패로는_닫힌채유지() {
        withCircuit(PUSH, circuit -> {
            fail(circuit, new QueryTimeoutException("timeout"));

            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        });
    }

    @Test
    void 쓰기서킷_두번연속실패하면_열림() {
        withCircuit(PUSH, circuit -> {
            fail(circuit, new QueryTimeoutException("timeout"));
            fail(circuit, new RedisConnectionFailureException("refused"));

            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        });
    }

    @Test
    void 쓰기서킷_실패사이에성공이끼면_닫힌채유지() {
        withCircuit(PUSH, circuit -> {
            fail(circuit, new QueryTimeoutException("timeout"));
            circuit.onSuccess(0, TimeUnit.MILLISECONDS);
            fail(circuit, new QueryTimeoutException("timeout"));

            assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        });
    }

    @Test
    void 쓰기서킷_시간초과와연결실패만실패로셈() {
        withCircuit(PUSH, circuit -> {
            var countsAsFailure = circuit.getCircuitBreakerConfig().getRecordExceptionPredicate();

            assertThat(countsAsFailure).accepts(new QueryTimeoutException("timeout"),
                    new RedisConnectionFailureException("refused"));
            // Redis는 살아 있는데 명령이 틀림(WRONGTYPE 등): 장애가 아니므로 서킷을 열지 않는다
            assertThat(countsAsFailure).rejects(new RedisSystemException("WRONGTYPE", null));
        });
    }

    @Test
    void 쓰기서킷_열리면_30초뒤복구확인() {
        withCircuit(PUSH, circuit -> assertThat(waitInOpenState(circuit.getCircuitBreakerConfig()))
                .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void 읽기서킷_최근15초구간_최소6건_실패율30_느린응답3점5초초과40() {
        withCircuit(POP, circuit -> {
            CircuitBreakerConfig config = circuit.getCircuitBreakerConfig();

            assertThat(config.getSlidingWindowType()).isEqualTo(SlidingWindowType.TIME_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(15);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(6);
            assertThat(config.getFailureRateThreshold()).isEqualTo(30f);
            assertThat(config.getSlowCallRateThreshold()).isEqualTo(40f);
            assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofMillis(3500));
            assertThat(waitInOpenState(config)).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void 큐길이게이지는_쓰기서킷과_다른서킷을씀() throws NoSuchMethodException {
        // 게이지 조회가 쓰기 서킷을 같이 쓰면 게이지 실패도 '연속 2번 실패'에 섞여 발행 서킷을 연다
        String publishCircuit = TaskQueueManager.class.getMethod("publishTasks", List.class)
                .getAnnotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker.class).name();
        String queueSizeCircuit = TaskQueueManager.class.getMethod("getQueueSizeSafely")
                .getAnnotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker.class).name();

        assertThat(publishCircuit).isEqualTo(PUSH);
        assertThat(queueSizeCircuit).isEqualTo(QUEUE_SIZE);
    }

    @Test
    void 큐길이게이지서킷_시간초과와연결실패만실패로셈() {
        withCircuit(QUEUE_SIZE, circuit -> {
            var countsAsFailure = circuit.getCircuitBreakerConfig().getRecordExceptionPredicate();

            assertThat(countsAsFailure).accepts(new QueryTimeoutException("timeout"),
                    new RedisConnectionFailureException("refused"));
            assertThat(countsAsFailure).rejects(new RedisSystemException("WRONGTYPE", null));
        });
    }

    private static void fail(CircuitBreaker circuit, Throwable error) {
        circuit.onError(0, TimeUnit.MILLISECONDS, error);
    }

    private static Duration waitInOpenState(CircuitBreakerConfig config) {
        return Duration.ofMillis(config.getWaitIntervalFunctionInOpenState().apply(1));
    }

    private static void withCircuit(String name, Consumer<CircuitBreaker> assertion) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(mainApplicationYml()))
                .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
                .run(context -> assertion.accept(context.getBean(CircuitBreakerRegistry.class).circuitBreaker(name)));
    }

    private static PropertySource<?> mainApplicationYml() {
        try {
            List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                    .load("main-application-yml", new FileSystemResource("src/main/resources/application.yml"));
            return documents.getFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
