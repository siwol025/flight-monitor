package com.siwol025.flight_monitor.auth.resolver;

import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserArgumentResolverTest {

    private JwtProvider jwtProvider;
    private UserService userService;
    private AuthUserArgumentResolver resolver;
    private NativeWebRequest webRequest;
    private MethodParameter parameter;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        userService = mock(UserService.class);
        resolver = new AuthUserArgumentResolver(jwtProvider, userService);
        webRequest = mock(NativeWebRequest.class);
        parameter = mock(MethodParameter.class);
    }

    @Test
    void resolveArgument_토큰길이초과_UnauthorizedException_발생() {
        String oversizedToken = "Bearer " + "a".repeat(2049);
        when(webRequest.getHeader("Authorization")).thenReturn(oversizedToken);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgument_정상토큰_getUser_호출됨() {
        String validToken = "valid-token";
        when(webRequest.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        Claims claims = mock(Claims.class);
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(jwtProvider.parseToken(validToken)).thenReturn(claims);

        User mockUser = User.builder()
                .email("test@example.com")
                .name("테스트유저")
                .provider(Provider.GOOGLE)
                .providerId("provider-id")
                .role(Role.USER)
                .build();
        when(userService.getByUserId(1L)).thenReturn(mockUser);

        Object result = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(result).isSameAs(mockUser);
    }
}
