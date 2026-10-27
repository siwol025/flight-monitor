package com.siwol025.flight_monitor.monitor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 레이트 리미터·세마포어·Duffel 커넥션 풀 설정이 기동 때 어떻게 묶이는지 검사한다(Redis 없이).
 */
class RateLimiterSettingsTest {

    @ParameterizedTest(name = "limit={0}, period={1}")
    @CsvSource({"0, 60s, monitor.ratelimiter.limit", "27, 0s, monitor.ratelimiter.period"})
    void 설정_limit0또는period0_기동실패(String limit, String period, String rejectedKey) {
        // 0이면 토큰 간격이 0으로 나누기가 되거나 영원히 기다려, 폴러가 오류 없이 멈춘 것처럼 보인다
        new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withUserConfiguration(RedisRateLimiter.class)
                .withPropertyValues("monitor.ratelimiter.enabled=true",
                        "monitor.ratelimiter.limit=" + limit, "monitor.ratelimiter.period=" + period)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(rejectedKey);
                });
    }

    @Test
    void 설정_서버대수0이하_기동실패() {
        // Redis 장애 중 로컬 버킷 간격은 기간 × 서버 대수 ÷ 한도다. 0이면 간격이 0이 되어 한도 없이 내준다
        new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withUserConfiguration(RedisRateLimiter.class)
                .withPropertyValues("monitor.ratelimiter.enabled=true", "monitor.ratelimiter.limit=27",
                        "monitor.ratelimiter.period=60s", "monitor.ratelimiter.instances=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("monitor.ratelimiter.instances");
                });
    }

    @Test
    void 환경변수로_한도와maxInFlight_재정의됨() {
        // 값은 yml에서 바꾸고 같은 키의 환경변수로 덮어쓸 수도 있다(새 프로필 파일 없음). 환경변수가 메인 yml 기본값을 이겨야 한다
        deployedSettingsWithEnvironment(Map.of(
                "MONITOR_RATELIMITER_LIMIT", "100",
                "MONITOR_RATELIMITER_PERIOD", "1s",
                "MONITOR_RATELIMITER_INSTANCES", "3",
                "MONITOR_BACKPRESSURE_MAX_IN_FLIGHT", "100",
                "MONITOR_HTTP_MAX_CONNECTIONS", "100"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimiterProperties.class))
                            .isEqualTo(new RateLimiterProperties(100, Duration.ofSeconds(1), 3));
                    assertThat(context.getBean(InFlightBackpressure.class).getMaxInFlight()).isEqualTo(100);
                    assertThat(context).hasBean("duffelHttpClient");
                });
    }

    @Test
    void 환경변수로_maxInFlight만올리면_기동실패() {
        // 동시 실행 상한과 Duffel 커넥션 풀은 함께 움직인다. 풀만 기본값(7)으로 남으면 풀이 새 병목이 되므로 기동 때 알린다
        deployedSettingsWithEnvironment(Map.of("MONITOR_BACKPRESSURE_MAX_IN_FLIGHT", "100"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("max-connections(7)")
                            .hasMessageContaining("max-in-flight(100)");
                });
    }

    @Test
    void 설정_레이트리미터서킷_시간초과와연결실패만실패로셈() {
        // yml에 이 서킷이 없거나 이름이 다르면 기본 설정이 모든 예외를 실패로 센다. 그러면 WRONGTYPE 같은 버그도
        // 로컬 폴백 뒤로 조용히 숨는다
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(mainApplicationYml()))
                .withConfiguration(AutoConfigurations.of(CircuitBreakerAutoConfiguration.class))
                .run(context -> {
                    Predicate<Throwable> countsAsFailure = context.getBean(CircuitBreakerRegistry.class)
                            .circuitBreaker(RedisRateLimiter.CIRCUIT_BREAKER_NAME)
                            .getCircuitBreakerConfig()
                            .getRecordExceptionPredicate();
                    // Redis가 응답하지 않음(명령 시간 초과), 연결 실패
                    assertThat(countsAsFailure).accepts(new QueryTimeoutException("timeout"),
                            new RedisConnectionFailureException("refused"));
                    // Redis는 살아 있는데 명령이 틀림(WRONGTYPE 등): 폴백하지 않고 드러나야 한다
                    assertThat(countsAsFailure).rejects(new RedisSystemException("WRONGTYPE", null));
                });
    }

    /**
     * 실제 배포 기본값(메인 application.yml) 위에 환경변수를 얹은 컨텍스트. 테스트 클래스패스의 application.yml은
     * 테스트 것이라 메인 파일을 경로로 읽는다. 환경변수 원천의 이름은 systemEnvironment여야 Binder가
     * 환경변수 이름 규칙(MONITOR_RATELIMITER_LIMIT → monitor.ratelimiter.limit)을 쓴다.
     */
    private static ApplicationContextRunner deployedSettingsWithEnvironment(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    MutablePropertySources sources = context.getEnvironment().getPropertySources();
                    sources.addLast(mainApplicationYml());
                    sources.addFirst(new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment));
                })
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withUserConfiguration(RedisRateLimiter.class, InFlightBackpressure.class, DuffelHttpClientConfig.class);
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
