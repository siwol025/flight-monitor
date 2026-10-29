package com.siwol025.flight_monitor.monitor.worker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * 테스트용 레이트 리미터 서킷. 운영 yml(redisRateLimiterCircuitBreaker)과 같이 Redis 무응답·연결 실패만 실패로 센다.
 * OPEN 유지 시간만 테스트가 짧게 정한다.
 */
final class RateLimiterCircuits {

    private RateLimiterCircuits() {
    }

    static CircuitBreakerRegistry likeProduction(Duration waitInOpenState) {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(waitInOpenState)
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(QueryTimeoutException.class, RedisConnectionFailureException.class)
                .build());
    }
}
