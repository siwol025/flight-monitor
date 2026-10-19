package com.siwol025.flight_monitor.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.auth.dto.response.TokenResponse;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTokenTypeTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("test-secret-key-must-be-at-least-32-bytes!!", 3_600_000L, 1_209_600_000L);

    private final TokenResponse tokens = jwtProvider.createToken(7L);

    @Test
    void getUserIdFromAccessToken_access토큰이면_userId반환() {
        assertThat(jwtProvider.getUserIdFromAccessToken(tokens.accessToken())).isEqualTo(7L);
    }

    @Test
    void getUserIdFromAccessToken_refresh토큰이면_예외발생() {
        assertThatThrownBy(() -> jwtProvider.getUserIdFromAccessToken(tokens.refreshToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void getUserIdFromRefreshToken_refresh토큰이면_userId반환() {
        assertThat(jwtProvider.getUserIdFromRefreshToken(tokens.refreshToken())).isEqualTo(7L);
    }

    @Test
    void getUserIdFromRefreshToken_access토큰이면_예외발생() {
        assertThatThrownBy(() -> jwtProvider.getUserIdFromRefreshToken(tokens.accessToken()))
                .isInstanceOf(JwtException.class);
    }
}
