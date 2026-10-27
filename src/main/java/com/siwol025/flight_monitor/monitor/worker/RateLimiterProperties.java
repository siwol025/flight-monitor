package com.siwol025.flight_monitor.monitor.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 레이트 리미터 설정: 기간당 N건. 빠른 실험은 환경변수(MONITOR_RATELIMITER_LIMIT, MONITOR_RATELIMITER_PERIOD)로만 올린다.
 */
@ConfigurationProperties("monitor.ratelimiter")
public record RateLimiterProperties(int limit, Duration period) {

    public RateLimiterProperties {
        // 0이면 토큰 간격 계산이 0으로 나누기가 되거나 영원히 기다리므로 기동 때 막는다
        if (limit <= 0) {
            throw new IllegalArgumentException("monitor.ratelimiter.limit은 1 이상이어야 한다: " + limit);
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("monitor.ratelimiter.period는 0보다 커야 한다: " + period);
        }
    }
}
