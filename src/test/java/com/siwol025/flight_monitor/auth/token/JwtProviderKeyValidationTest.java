package com.siwol025.flight_monitor.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderKeyValidationTest {

    @Test
    void jwtProvider_키길이_32바이트_미만시_IllegalArgumentException_발생() {
        // "short-key"는 9바이트 → 256비트 미달
        assertThatThrownBy(() -> new JwtProvider("short-key", 3600000L, 86400000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256");
    }
}
