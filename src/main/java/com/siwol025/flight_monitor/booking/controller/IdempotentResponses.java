package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 멱등키 처리 결과를 HTTP 응답으로 바꾼다(구매·승인 API가 같이 쓴다) */
final class IdempotentResponses {

    static final String HEADER = "Idempotency-Key";

    // idempotency_keys.idem_key 칼럼 길이
    private static final int MAX_KEY_LENGTH = 64;

    private IdempotentResponses() {
    }

    /** 키가 없거나 비었거나 너무 길면 400 IDEMPOTENCY_KEY_REQUIRED */
    static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException(ErrorTag.IDEMPOTENCY_KEY_REQUIRED);
        }
        return key;
    }

    static ResponseEntity<?> toResponse(IdempotentResult<?> result) {
        return switch (result) {
            case IdempotentResult.Fresh<?> fresh -> fresh.response();
            // 저장된 상태 코드와 JSON 본문을 그대로 돌려준다
            case IdempotentResult.Replay<?> replay -> ResponseEntity.status(replay.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(replay.body());
            case IdempotentResult.Processing<?> processing -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .header(HttpHeaders.RETRY_AFTER, "2")
                    .body(ErrorResponse.from(ErrorTag.IDEMPOTENT_REQUEST_PROCESSING));
            case IdempotentResult.KeyReused<?> keyReused -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse.from(ErrorTag.IDEMPOTENCY_KEY_REUSED));
        };
    }
}
