package com.siwol025.flight_monitor.booking.idempotency;

import org.springframework.http.ResponseEntity;

/** 멱등키 처리 결과. 컨트롤러가 HTTP 응답으로 바꾼다 */
public sealed interface IdempotentResult<T> {

    /** 이 요청이 처리한 응답 */
    record Fresh<T>(ResponseEntity<T> response) implements IdempotentResult<T> {
    }

    /** 같은 키의 저장된 응답(상태 코드와 JSON 본문 그대로) */
    record Replay<T>(int status, String body) implements IdempotentResult<T> {
    }

    /** 같은 키를 다른 요청이 처리 중(409 IDEMPOTENT_REQUEST_PROCESSING) */
    record Processing<T>() implements IdempotentResult<T> {
    }

    /** 같은 키, 다른 본문(422 IDEMPOTENCY_KEY_REUSED) */
    record KeyReused<T>() implements IdempotentResult<T> {
    }
}
