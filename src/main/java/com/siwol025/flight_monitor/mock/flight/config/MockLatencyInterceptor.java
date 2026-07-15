package com.siwol025.flight_monitor.mock.flight.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@Profile("mock")
@RequiredArgsConstructor
public class MockLatencyInterceptor implements HandlerInterceptor {

    private final MockLatencyInjector mockLatencyInjector;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
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
