package com.siwol025.flight_monitor.auth.dto.request;

import com.siwol025.flight_monitor.auth.controller.AuthController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInputValidationTest {

    @Test
    @DisplayName("LoginRequest_idToken_필드에_NotBlank_어노테이션이_있어야한다")
    void LoginRequest_idToken_필드에_NotBlank_어노테이션이_있어야한다() throws NoSuchFieldException {
        Field idTokenField = LoginRequest.class.getDeclaredField("idToken");

        NotBlank notBlank = idTokenField.getAnnotation(NotBlank.class);

        assertThat(notBlank)
                .as("LoginRequest.idToken 필드에 @NotBlank 어노테이션이 없습니다")
                .isNotNull();
    }

    @Test
    @DisplayName("RefreshTokenRequest_refreshToken_필드에_NotBlank_어노테이션이_있어야한다")
    void RefreshTokenRequest_refreshToken_필드에_NotBlank_어노테이션이_있어야한다() throws NoSuchFieldException {
        Field refreshTokenField = RefreshTokenRequest.class.getDeclaredField("refreshToken");

        NotBlank notBlank = refreshTokenField.getAnnotation(NotBlank.class);

        assertThat(notBlank)
                .as("RefreshTokenRequest.refreshToken 필드에 @NotBlank 어노테이션이 없습니다")
                .isNotNull();
    }

    @Test
    @DisplayName("AuthController_login과_refresh_RequestBody에_Valid_어노테이션이_있어야한다")
    void AuthController_login과_refresh_RequestBody에_Valid_어노테이션이_있어야한다() {
        Method[] methods = AuthController.class.getDeclaredMethods();

        int validatedCount = 0;
        for (Method method : methods) {
            PostMapping postMapping = method.getAnnotation(PostMapping.class);
            if (postMapping == null) continue;

            String[] paths = postMapping.value();
            boolean isLoginOrRefresh = false;
            for (String path : paths) {
                if (path.equals("/login/google") || path.equals("/token")) {
                    isLoginOrRefresh = true;
                    break;
                }
            }
            if (!isLoginOrRefresh) continue;

            for (Parameter param : method.getParameters()) {
                if (param.getAnnotation(RequestBody.class) != null) {
                    Valid valid = param.getAnnotation(Valid.class);
                    assertThat(valid)
                            .as("AuthController.%s()의 @RequestBody 파라미터에 @Valid 어노테이션이 없습니다", method.getName())
                            .isNotNull();
                    validatedCount++;
                }
            }
        }

        assertThat(validatedCount)
                .as("login과 refresh 두 엔드포인트 모두 @Valid @RequestBody 파라미터가 있어야 합니다")
                .isEqualTo(2);
    }
}
