package com.siwol025.flight_monitor.auth.service;

import com.siwol025.flight_monitor.auth.token.GoogleTokenParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceOcpTest {

    @Test
    void AuthService가_GoogleTokenParser_구체클래스_필드를_갖지_않아야한다() {
        boolean hasConcreteField = Arrays.stream(AuthService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(GoogleTokenParser.class));
        assertThat(hasConcreteField).isFalse();
    }

    @Test
    void AuthService에_JWT예외_공통_변환_private_메서드가_존재한다() throws Exception {
        java.lang.reflect.Method method = AuthService.class
                .getDeclaredMethod("translateJwtException", Exception.class);
        assertThat(java.lang.reflect.Modifier.isPrivate(method.getModifiers())).isTrue();
    }
}
