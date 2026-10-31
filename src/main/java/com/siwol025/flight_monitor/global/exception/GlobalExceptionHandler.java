package com.siwol025.flight_monitor.global.exception;

import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import com.siwol025.flight_monitor.global.exception.custom.OfferPriceChangedException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpStatusException.class)
    public ResponseEntity<ErrorResponse> handleHttpStatusException(HttpStatusException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ErrorResponse.from(e.getErrorTag()));
    }

    // HttpStatusException보다 구체적인 타입이라 이 처리기가 먼저 선택된다
    @ExceptionHandler(OfferPriceChangedException.class)
    public ResponseEntity<PriceChangedErrorResponse> handleOfferPriceChanged(OfferPriceChangedException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(PriceChangedErrorResponse.from(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorTag.INVALID_INPUT.name(), message));
    }

    // 본문을 해석할 수 없음(깨진 JSON, 2026-13-40 같은 날짜). 검증 전에 실패하는 클라이언트 입력 오류다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorTag.INVALID_INPUT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
