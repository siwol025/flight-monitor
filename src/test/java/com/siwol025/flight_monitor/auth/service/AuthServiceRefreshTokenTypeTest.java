package com.siwol025.flight_monitor.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.auth.dto.request.RefreshTokenRequest;
import com.siwol025.flight_monitor.auth.dto.response.TokenResponse;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthServiceRefreshTokenTypeTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("test-secret-key-must-be-at-least-32-bytes!!", 3_600_000L, 1_209_600_000L);
    private final UserService userService = mock(UserService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthService authService =
            new AuthService(List.of(), userService, jwtProvider, refreshTokenService);

    @Test
    void refresh_access토큰으로_재발급시_DB조회없이_UnauthorizedException_발생() {
        // given
        TokenResponse tokens = jwtProvider.createToken(1L);

        // when & then
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(tokens.accessToken())))
                .isInstanceOf(UnauthorizedException.class);
        verify(userService, never()).getByUserId(any());
        verify(refreshTokenService, never()).getByUser(any());
    }
}
