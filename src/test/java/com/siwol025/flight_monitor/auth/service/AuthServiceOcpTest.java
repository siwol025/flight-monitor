package com.siwol025.flight_monitor.auth.service;

import com.siwol025.flight_monitor.auth.dto.request.LoginRequest;
import com.siwol025.flight_monitor.auth.dto.response.LoginResponse;
import com.siwol025.flight_monitor.auth.dto.response.TokenResponse;
import com.siwol025.flight_monitor.auth.token.IdTokenParser;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceOcpTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private User mockUser;

    @Test
    void AuthService에_JWT예외_공통_변환_private_메서드가_존재한다() throws Exception {
        java.lang.reflect.Method method = AuthService.class
                .getDeclaredMethod("translateJwtException", Exception.class);
        assertThat(java.lang.reflect.Modifier.isPrivate(method.getModifiers())).isTrue();
    }

    @Test
    void login_지원하는_Provider_요청시_해당_IdTokenParser로_라우팅되어_LoginResponse_반환됨() {
        IdTokenParser googleParser = new IdTokenParser() {
            @Override
            public Provider getProvider() { return Provider.GOOGLE; }
            @Override
            public String getProviderId(String idToken) { return "google-provider-id"; }
            @Override
            public String getEmail(String idToken) { return "test@example.com"; }
            @Override
            public String getName(String idToken) { return "Test User"; }
        };

        AuthService authService = new AuthService(List.of(googleParser), userService, jwtProvider, refreshTokenService);

        LoginRequest request = new LoginRequest("valid-id-token");

        given(userService.isFirstLogin(Provider.GOOGLE, "google-provider-id")).willReturn(false);
        given(userService.findOrCreateUser(any(Provider.class), anyString(), anyString(), anyString()))
                .willReturn(mockUser);
        given(mockUser.getId()).willReturn(1L);
        given(jwtProvider.createToken(anyLong()))
                .willReturn(TokenResponse.builder().accessToken("access").refreshToken("refresh").build());
        given(jwtProvider.getIssuedAt(anyString())).willReturn(LocalDateTime.now());
        given(jwtProvider.getExpiration(anyString())).willReturn(LocalDateTime.now().plusDays(14));
        given(jwtProvider.hashToken(anyString())).willReturn("hashed-refresh");

        LoginResponse response = authService.login(request, Provider.GOOGLE);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void login_등록되지_않은_Provider_요청시_UnauthorizedException_UNAUTHORIZED_태그_발생() {
        AuthService authService = new AuthService(List.of(), userService, jwtProvider, refreshTokenService);

        LoginRequest request = new LoginRequest("any-id-token");

        assertThatThrownBy(() -> authService.login(request, Provider.GOOGLE))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(ex -> {
                    UnauthorizedException uae = (UnauthorizedException) ex;
                    assertThat(uae.getErrorTag()).isEqualTo(ErrorTag.UNAUTHORIZED);
                });
    }
}
