package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Duffel 오류 본문 중 원인 추적·분기에 쓰는 errors[].type·code와 meta.request_id만. 그 밖의 필드(source는 문자열이기도
 * 객체이기도 하다)는 읽지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DuffelErrorResponse(List<Error> errors, Meta meta) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String type, String code) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(@JsonProperty("request_id") String requestId) {
    }
}
