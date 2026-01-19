package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class UnauthorizedException extends HttpStatusException {

    public UnauthorizedException(ErrorTag errorTag) {
        super(HttpStatus.UNAUTHORIZED, errorTag);
    }
}
