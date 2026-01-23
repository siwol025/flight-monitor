package com.siwol025.flight_monitor.global.exception;

public enum ErrorTag {

    // 401 Unauthorized
    UNAUTHORIZED("토큰 기반 인증에 실패했습니다."),
    ID_TOKEN_NOT_VALID("유효하지 않은 id token입니다."),
    REFRESH_TOKEN_EXPIRED("refresh token이 만료됐습니다."),
    REFRESH_TOKEN_SIGNATURE_INVALID("refresh token이 위조됐습니다."),
    REFRESH_TOKEN_NOT_FOUND("refresh token을 찾을 수 없습니다."),
    REFRESH_TOKEN_INVALID("유효하지 않은 refresh token입니다."),

    // 404 Not Found
    USER_NOT_FOUND("회원을 찾을 수 없습니다.");

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
