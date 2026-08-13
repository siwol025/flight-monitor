package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Redis Pop 서킷브레이커 일관성 불변식 가드:
 * {@code monitor.consumer.block-pop-timeout-seconds >= slowCallDurationThreshold} 이면
 * (블로킹 BRPOP 타임아웃이 느린 호출 임계 이상이면) 스프링 컨텍스트가 기동 단계에서
 * fail-fast 로 실패해야 한다.
 *
 * <p>타임아웃이 임계와 같거나 크면, 큐가 비어 있을 때 정상적으로 임계까지 블로킹된 BRPOP 이
 * "느린 호출"로 오분류되어 {@code redisPopCircuitBreaker} 가 헛되이 OPEN 된다.
 * 현재는 검증이 없어 충돌하는(3s >= 3s) 설정으로도 컨텍스트가 정상 기동한다.
 * 이 테스트는 그 공백을 드러낸다.
 */
class RedisPopCircuitBreakerConsistencyGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // Duration 프로퍼티(3s 등)를 @Value 로 바인딩하려면 Spring Boot 의 변환 서비스가 필요하다.
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(RedisPopCircuitBreakerConsistencyValidator.class);

    @Test
    void 컨텍스트기동_popTimeout이slowCall임계이상이면_실패한다() {
        contextRunner
                .withPropertyValues(
                        "monitor.consumer.block-pop-timeout-seconds=3",
                        "resilience4j.circuitbreaker.instances.redisPopCircuitBreaker.slowCallDurationThreshold=3s")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("block-pop-timeout")
                        .hasMessageContaining("slowCallDurationThreshold"));
    }
}
