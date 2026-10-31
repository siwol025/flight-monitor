package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends HttpStatusException {

    public ServiceUnavailableException(ErrorTag errorTag) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorTag);
    }
}
