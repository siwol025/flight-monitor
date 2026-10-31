package com.siwol025.flight_monitor.mock.flight.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MockLatencyInterceptorTest {

    @Test
    void 지연_Duffel검색POST_지연적용() {
        MockLatencyInterceptor interceptor = interceptor(50);

        Duration elapsed = preHandle(interceptor, new MockHttpServletRequest("POST", "/air/offer_requests"));

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

    @Test
    void 지연_관리API_POST_지연없음() {
        // 잘못 지연되면 5초가 걸리므로 1초 기준으로 흔들림 없이 구분된다
        MockLatencyInterceptor interceptor = interceptor(5_000);

        Duration elapsed = preHandle(interceptor, new MockHttpServletRequest("POST", "/external/api/flights/1"));

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }

    private static MockLatencyInterceptor interceptor(long baseMs) {
        MockLatencyProperties properties = new MockLatencyProperties();
        properties.setEnabled(true);
        properties.setBaseMs(baseMs);
        return new MockLatencyInterceptor(new MockLatencyInjector(properties));
    }

    private static Duration preHandle(MockLatencyInterceptor interceptor, MockHttpServletRequest request) {
        long start = System.nanoTime();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
