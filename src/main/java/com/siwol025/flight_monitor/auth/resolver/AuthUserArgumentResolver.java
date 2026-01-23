package com.siwol025.flight_monitor.auth.resolver;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtProvider jwtProvider;
    private final UserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String bearer = webRequest.getHeader("Authorization");

        if (bearer != null && bearer.startsWith("Bearer ")) {
            String accessToken = bearer.substring(7); // "Bearer " 길이 = 7
            return getUser(accessToken);
        }
        throw new UnauthorizedException(ErrorTag.UNAUTHORIZED);
    }

    public User getUser(String accessToken) {
        try {
            Long userId = jwtProvider.parseToken(accessToken).get("userId", Long.class);
            return userService.getByUserId(userId);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(ErrorTag.ACCESS_TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new UnauthorizedException(ErrorTag.ACCESS_TOKEN_SIGNATURE_INVALID);
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorTag.UNAUTHORIZED);
        }
    }
}
