package com.siwol025.flight_monitor.mock.flight.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("mock")
@RequiredArgsConstructor
public class MockLatencyWebMvcConfig implements WebMvcConfigurer {

    private static final String FLIGHT_BY_ID_PATTERN = "/external/api/flights/*";
    private static final String FLIGHT_SEARCH_PATTERN = "/external/api/flights/search";

    private final MockLatencyInterceptor mockLatencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mockLatencyInterceptor)
                .addPathPatterns(FLIGHT_BY_ID_PATTERN)
                .excludePathPatterns(FLIGHT_SEARCH_PATTERN);
    }
}
