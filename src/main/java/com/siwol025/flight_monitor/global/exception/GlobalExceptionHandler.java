package com.siwol025.flight_monitor.global.exception;

import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorTag.INVALID_INPUT.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
