package com.siwol025.flight_monitor.mock.duffel;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * mock이 강제하는 업체 호출 한도(기간당 N건, 고정 창). 실제 Duffel처럼 창은 기간 경계에 맞추고(창 시작 = epoch를 기간으로 내림),
 * 한도를 넘은 요청도 기간당 요청 수에 센다. 여러 대 검증에서 앱들의 합계 요청률을 이 한도로 잰다.
 */
@Component
public class MockDuffelRateLimitEnforcer {

    private final int limit;
    private final long periodMillis;

    private long windowStartMillis = Long.MIN_VALUE;
    private int used;

    public MockDuffelRateLimitEnforcer(@Value("${mock.duffel.rate-limit.limit:30}") int limit,
                                       @Value("${mock.duffel.rate-limit.period:60s}") Duration period) {
        this.limit = limit;
        this.periodMillis = period.toMillis();
    }

    public synchronized RateLimitStatus acquire(Instant now) {
        long windowStart = Math.floorDiv(now.toEpochMilli(), periodMillis) * periodMillis;
        if (windowStart != windowStartMillis) {
            windowStartMillis = windowStart;
            used = 0;
        }
        used++;
        return new RateLimitStatus(used <= limit, limit, Math.max(0, limit - used),
                Instant.ofEpochMilli(windowStart + periodMillis));
    }

    /**
     * 이번 요청의 판정과 Duffel 응답 헤더 값. 헤더는 429만이 아니라 모든 응답에 붙는다.
     */
    public record RateLimitStatus(boolean allowed, int limit, int remaining, Instant resetAt) {

        // ratelimit-reset은 창이 끝나는 시각, RFC 2616 HTTP 날짜(예: Sun, 25 Oct 2026 16:08:00 GMT)
        public HttpHeaders toHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("ratelimit-limit", String.valueOf(limit));
            headers.set("ratelimit-remaining", String.valueOf(remaining));
            headers.set("ratelimit-reset", DateTimeFormatter.RFC_1123_DATE_TIME.format(resetAt.atZone(ZoneOffset.UTC)));
            return headers;
        }
    }
}
