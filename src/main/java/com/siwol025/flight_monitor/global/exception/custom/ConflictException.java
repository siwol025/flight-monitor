package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class ConflictException extends HttpStatusException {

    public ConflictException(ErrorTag errorTag) {
        super(HttpStatus.CONFLICT, errorTag);
    }
}
