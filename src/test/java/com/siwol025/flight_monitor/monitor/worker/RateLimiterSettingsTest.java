package com.siwol025.flight_monitor.monitor.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
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
                .withUserConfiguration(RedisRateLimiter.class)
                .withPropertyValues("monitor.ratelimiter.enabled=true",
                        "monitor.ratelimiter.limit=" + limit, "monitor.ratelimiter.period=" + period)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(rejectedKey);
                });
    }

    @Test
    void 환경변수로_한도와maxInFlight_재정의됨() {
        // 빠른 실험은 환경변수로만 올린다(새 프로필 파일 없음). 환경변수가 메인 yml 기본값을 이겨야 한다
        deployedSettingsWithEnvironment(Map.of(
                "MONITOR_RATELIMITER_LIMIT", "100",
                "MONITOR_RATELIMITER_PERIOD", "1s",
                "MONITOR_BACKPRESSURE_MAX_IN_FLIGHT", "100",
                "MONITOR_HTTP_MAX_CONNECTIONS", "100"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimiterProperties.class))
                            .isEqualTo(new RateLimiterProperties(100, Duration.ofSeconds(1)));
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
