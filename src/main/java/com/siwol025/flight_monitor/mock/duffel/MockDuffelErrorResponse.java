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

    // 422: 매진(빈 좌석 없음). code는 Duffel의 다른 offer 엔드포인트에 있는 값이고, type은 mock이 정한 값이다
    public static MockDuffelErrorResponse offerNoLongerAvailable() {
        return new MockDuffelErrorResponse(
                List.of(new Error("invalid_state_error", "offer_no_longer_available", "Offer no longer available",
                        "The offer is no longer available. Search again for up-to-date offers.", DOCUMENTATION_URL)),
                new Meta("mock_" + UUID.randomUUID(), 422));
    }

    // 404: 형식이 맞지 않거나 없는 offer ID. type은 mock이 정한 값이다
    public static MockDuffelErrorResponse notFound() {
        return new MockDuffelErrorResponse(
                List.of(new Error("invalid_request_error", "not_found", "Not found",
                        "The resource you are trying to access does not exist.", DOCUMENTATION_URL)),
                new Meta("mock_" + UUID.randomUUID(), 404));
    }

    // 422: 주문 금액·통화가 현재 가격과 다름. code와 type은 mock이 정한 값이다
    public static MockDuffelErrorResponse priceChanged() {
        return new MockDuffelErrorResponse(
                List.of(new Error("invalid_state_error", "price_changed", "Price changed",
                        "The payment amount or currency does not match the current offer price.", DOCUMENTATION_URL)),
                new Meta("mock_" + UUID.randomUUID(), 422));
    }
}
