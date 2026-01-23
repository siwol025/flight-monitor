package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class BadRequestException extends HttpStatusException {

    public BadRequestException(ErrorTag errorTag) {
        super(HttpStatus.BAD_REQUEST, errorTag);
    }
}
