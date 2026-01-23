package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class NotFoundException extends HttpStatusException {

    public NotFoundException(ErrorTag errorTag) {
        super(HttpStatus.NOT_FOUND, errorTag);
    }
}
