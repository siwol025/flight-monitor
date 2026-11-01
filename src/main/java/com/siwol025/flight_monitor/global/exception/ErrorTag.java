package com.siwol025.flight_monitor.global.exception;

public enum ErrorTag {

    // 401 Unauthorized
    UNAUTHORIZED("토큰 기반 인증에 실패했습니다."),
    ID_TOKEN_NOT_VALID("유효하지 않은 id token입니다."),
    ACCESS_TOKEN_EXPIRED("access token이 만료됐습니다."),
    ACCESS_TOKEN_SIGNATURE_INVALID("access token이 위조됐습니다."),
    REFRESH_TOKEN_EXPIRED("refresh token이 만료됐습니다."),
    REFRESH_TOKEN_SIGNATURE_INVALID("refresh token이 위조됐습니다."),
    REFRESH_TOKEN_NOT_FOUND("refresh token을 찾을 수 없습니다."),
    REFRESH_TOKEN_INVALID("유효하지 않은 refresh token입니다."),

    // 404 Not Found
    USER_NOT_FOUND("회원을 찾을 수 없습니다."),
    FLIGHT_NOT_FOUND("해당 항공편 정보를 찾을 수 없습니다."),
    AIRLINE_NOT_FOUND("해당 항공사를 찾을 수 없습니다."),
    AIRPORT_NOT_FOUND("해당 공항을 찾을 수 없습니다."),
    SUBSCRIPTION_NOT_FOUND("구독내역을 찾을 수 없습니다."),
    SEAT_NOT_FOUND("해당 좌석을 찾을 수 없습니다."),
    SEAT_PRICE_NOT_FOUND("해당 좌석 등급의 가격 정보를 찾을 수 없습니다."),

    // 400 Bad Request
    INVALID_INPUT("입력값이 올바르지 않습니다."),
    DUPLICATE_SUBSCRIPTION("이미 등록된 구독입니다."),
    DUPLICATE_FLIGHT("이미 등록된 항공편입니다."),
    SAME_DEPARTURE_ARRIVAL_AIRPORT("출발 공항과 도착 공항이 같습니다."),
    INVALID_AIRPORT_CODE("공항 코드는 영문 3자여야 합니다."),
    PAST_DEPARTURE_DATE("출발일이 이미 지났습니다."),
    INVALID_ROUTE_SUBSCRIPTION("운임 업체가 조회할 수 없는 노선이라 구독할 수 없습니다."),
    PASSENGER_NOT_ADULT("승객은 출발일 기준 만 18세 이상이어야 합니다."),
    INVALID_ROUTE("항공권 업체가 조회할 수 없는 노선입니다."),

    // 403 Forbidden (resource ownership)
    UNAUTHORIZED_SUBSCRIPTION("해당 구독을 취소할 권한이 없습니다."),

    // 409 Conflict
    OFFER_UNAVAILABLE("예매할 수 없는 항공권입니다."),
    OFFER_PRICE_CHANGED("항공권 가격이 바뀌었습니다. 현재 가격을 확인해 주세요."),

    // 503 Service Unavailable
    FARE_PROVIDER_UNAVAILABLE("항공권 업체를 지금 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final String message;

    ErrorTag(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
