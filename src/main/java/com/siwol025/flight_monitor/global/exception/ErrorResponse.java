package com.siwol025.flight_monitor.global.exception;

public record ErrorResponse(
        String tag,
        String message
) {

    public static ErrorResponse from(ErrorTag tag) {
        return new ErrorResponse(tag.name(), tag.getMessage());
    }
}
