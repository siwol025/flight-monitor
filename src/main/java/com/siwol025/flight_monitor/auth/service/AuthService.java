package com.siwol025.flight_monitor.auth.service;

import com.siwol025.flight_monitor.auth.dto.request.LoginRequest;
import com.siwol025.flight_monitor.auth.dto.response.LoginResponse;
import com.siwol025.flight_monitor.auth.dto.response.TokenResponse;
import com.siwol025.flight_monitor.auth.token.GoogleTokenParser;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final GoogleTokenParser googleTokenParser;
    private final UserService  userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request, Provider provider) {
        if (provider == Provider.GOOGLE) {
            return loginWithGoogle(request);
        }

        throw new UnauthorizedException(ErrorTag.UNAUTHORIZED);
    }

    public LoginResponse loginWithGoogle(LoginRequest request) {
        String idToken = request.idToken();
        validateIdToken(idToken);;

        Provider provider = googleTokenParser.getProvider();
        String providerId = googleTokenParser.getProviderId(idToken);
        String email = googleTokenParser.getEmail(idToken);
        String name = googleTokenParser.getName(idToken);

        boolean isNewUser = userService.isFirstLogin(provider, providerId);

        // 회원 정보가 없으면 생성, 있으면 조회 (회원가입/로그인 통합)
        User user = userService.findOrCreateUser(provider, providerId, email, name);
        TokenResponse token = jwtProvider.createToken(user.getId());

        saveRefreshToken(user, token.refreshToken());

        return LoginResponse.of(token.accessToken(), token.refreshToken(), isNewUser);
    }

    private void saveRefreshToken(User user, String refreshToken) {
        try {
            LocalDateTime issuedAt = jwtProvider.getIssuedAt(refreshToken);
            LocalDateTime expiration = jwtProvider.getExpiration(refreshToken);
            refreshTokenService.save(user, jwtProvider.hashToken(refreshToken), issuedAt, expiration);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(ErrorTag.REFRESH_TOKEN_EXPIRED);
        } catch (SignatureException e) {
            throw new UnauthorizedException(ErrorTag.REFRESH_TOKEN_SIGNATURE_INVALID);
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorTag.UNAUTHORIZED);
        }
    }

    private void validateIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            //throw new BadRequestException(ErrorTag.ID_TOKEN_NOT_VALID);
        }
    }
}
