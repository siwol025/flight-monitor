package com.siwol025.flight_monitor.auth.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void loginRequest_idToken_빈값이면_메시지_포함됨() {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(new LoginRequest(""));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("idToken은 필수입니다.");
    }
}
