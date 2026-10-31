package com.siwol025.flight_monitor.mock.flight.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 등록된 mock 경로(MockLatencyWebMvcConfig)의 응답을 인위적으로 늦춘다. 관리 API는 GET(조회)만 늦추고 생성·수정은 그대로 두며,
 * Duffel 검색은 POST라 메서드와 상관없이 늦춘다.
 *
 * <p>지연이 컨트롤러보다 먼저 걸리므로 한도 초과(429) 응답도 같은 만큼 늦게 나간다.</p>
 */
@Slf4j
@Component
@Profile("mock")
@RequiredArgsConstructor
public class MockLatencyInterceptor implements HandlerInterceptor {

    private static final String DUFFEL_OFFER_REQUESTS_PATH = "/air/offer_requests";

    private final MockLatencyInjector mockLatencyInjector;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && !DUFFEL_OFFER_REQUESTS_PATH.equals(request.getRequestURI())) {
            return true;
        }

        long delayMs = mockLatencyInjector.computeDelayMs();
        if (delayMs <= 0) {
            return true;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return true;
    }
}
