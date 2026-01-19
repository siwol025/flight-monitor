package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class HttpStatusException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorTag errorTag;

    public HttpStatusException(HttpStatus status, ErrorTag errorTag) {
        super(errorTag.getMessage());
        this.status = status;
        this.errorTag = errorTag;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorTag getErrorTag() {
        return errorTag;
    }
}
