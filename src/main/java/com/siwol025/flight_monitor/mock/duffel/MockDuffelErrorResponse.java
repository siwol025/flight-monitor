package com.siwol025.flight_monitor.mock.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * Duffel 오류 응답 형식: errors[](type·code·title·message·documentation_url) + meta(request_id·status).
 */
public record MockDuffelErrorResponse(List<Error> errors, Meta meta) {

    private static final String DOCUMENTATION_URL = "https://duffel.com/docs/api/overview/errors";

    public record Error(String type, String code, String title, String message,
                        @JsonProperty("documentation_url") String documentationUrl) {
    }

    public record Meta(@JsonProperty("request_id") String requestId, int status) {
    }

    // 429: Duffel 문서의 rate_limit_error / rate_limit_exceeded
    public static MockDuffelErrorResponse rateLimitExceeded() {
        return new MockDuffelErrorResponse(
                List.of(new Error("rate_limit_error", "rate_limit_exceeded", "Too many requests",
                        "You have exceeded the rate limit. Retry after the time in the ratelimit-reset header.",
                        DOCUMENTATION_URL)),
                new Meta("mock_" + UUID.randomUUID(), 429));
    }
}
