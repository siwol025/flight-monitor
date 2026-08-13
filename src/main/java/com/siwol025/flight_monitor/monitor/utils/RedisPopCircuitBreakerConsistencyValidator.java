package com.siwol025.flight_monitor.monitor.utils;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis Pop 서킷브레이커 일관성 가드.
 *
 * <p>불변식: {@code monitor.consumer.block-pop-timeout-seconds}(블로킹 BRPOP 타임아웃) 는
 * {@code resilience4j.circuitbreaker.instances.redisPopCircuitBreaker.slowCallDurationThreshold}
 * (느린 호출 판정 임계) 보다 <b>엄격히 작아야</b> 한다. 두 값이 같거나 타임아웃이 더 크면,
 * 큐가 비어 있을 때(IDLE) 정상적으로 임계 시간까지 블로킹된 BRPOP 이 "느린 호출"로 오분류되어
 * 서킷브레이커가 헛되이 OPEN 된다.
 *
 * <p>깨진 설정이면 오조작이 런타임에 서킷을 헛열기 전에 빈 초기화 단계에서 fail-fast 해야 한다.
 * 이 불변식은 {@code RedisPopCircuitBreakerConsistencyGuardTest} 가 가드한다.
 */
@Component
public class RedisPopCircuitBreakerConsistencyValidator {

    private final long blockPopTimeoutSeconds;
    private final Duration slowCallDurationThreshold;

    public RedisPopCircuitBreakerConsistencyValidator(
            @Value("${monitor.consumer.block-pop-timeout-seconds:3}") long blockPopTimeoutSeconds,
            @Value("${resilience4j.circuitbreaker.instances.redisPopCircuitBreaker.slowCallDurationThreshold}")
            Duration slowCallDurationThreshold) {
        this.blockPopTimeoutSeconds = blockPopTimeoutSeconds;
        this.slowCallDurationThreshold = slowCallDurationThreshold;

        if (Duration.ofSeconds(blockPopTimeoutSeconds).compareTo(slowCallDurationThreshold) >= 0) {
            throw new IllegalStateException(
                    "monitor.consumer.block-pop-timeout-seconds(" + blockPopTimeoutSeconds
                            + "s)는 resilience4j.circuitbreaker.instances.redisPopCircuitBreaker"
                            + ".slowCallDurationThreshold(" + slowCallDurationThreshold
                            + ") 보다 엄격히 작아야 한다. 타임아웃이 임계와 같거나 크면, 큐가 비어 있을 때 정상적으로"
                            + " 임계 시간까지 블로킹된 BRPOP 이 \"느린 호출\"로 오분류되어"
                            + " redisPopCircuitBreaker 가 헛되이 OPEN 된다.");
        }
    }
}
