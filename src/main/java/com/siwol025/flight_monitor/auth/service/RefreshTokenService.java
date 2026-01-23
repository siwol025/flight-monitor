package com.siwol025.flight_monitor.auth.service;

import com.siwol025.flight_monitor.auth.domain.RefreshToken;
import com.siwol025.flight_monitor.auth.repository.RefreshTokenRepository;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void save(User user, String hashedRefreshToken, LocalDateTime issuedAt, LocalDateTime expiration) {
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();
        refreshTokenRepository.save(new RefreshToken(user, hashedRefreshToken, issuedAt, expiration));
    }

    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    public RefreshToken getByUser(User user) {
        return refreshTokenRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException(ErrorTag.REFRESH_TOKEN_NOT_FOUND));
    }

}
