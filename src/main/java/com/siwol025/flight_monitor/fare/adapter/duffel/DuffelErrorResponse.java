package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Duffel 오류 본문 중 422의 의미를 가르는 데 쓰는 errors[].code만.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DuffelErrorResponse(List<Error> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String code) {
    }
}
