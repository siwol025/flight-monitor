package com.siwol025.flight_monitor.monitor.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 레이트 리미터 설정: 기간당 N건과 서버 대수. 값은 yml({@code monitor.ratelimiter.*})에서 바꾸고, 같은 키의
 * 환경변수(MONITOR_RATELIMITER_LIMIT, MONITOR_RATELIMITER_PERIOD, MONITOR_RATELIMITER_INSTANCES)로 덮어쓸 수도 있다.
 *
 * @param instances 서버 대수. Redis 장애 중에는 서버마다 전역 한도 ÷ 대수로 따로 센다(로컬 폴백)
 */
@ConfigurationProperties("monitor.ratelimiter")
public record RateLimiterProperties(int limit, Duration period, int instances) {

    public RateLimiterProperties {
        // 0이면 토큰 간격 계산이 0으로 나누기가 되거나 영원히 기다리므로 기동 때 막는다
        if (limit <= 0) {
            throw new IllegalArgumentException("monitor.ratelimiter.limit은 1 이상이어야 한다: " + limit);
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("monitor.ratelimiter.period는 0보다 커야 한다: " + period);
        }
        // 0이면 로컬 폴백 간격(기간 × 대수 ÷ 한도)이 0이 되어 Redis 장애 중 한도 없이 내준다
        if (instances <= 0) {
            throw new IllegalArgumentException("monitor.ratelimiter.instances는 1 이상이어야 한다: " + instances);
        }
    }
}
