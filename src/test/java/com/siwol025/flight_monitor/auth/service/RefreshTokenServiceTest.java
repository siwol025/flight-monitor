package com.siwol025.flight_monitor.auth.service;

import com.siwol025.flight_monitor.auth.domain.RefreshToken;
import com.siwol025.flight_monitor.auth.repository.RefreshTokenRepository;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
    }

    @Test
    void getByUser_토큰_없을때_UnauthorizedException_발생() {
        // given
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        // when / then
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.getByUser(user));
        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void getByUser_정상흐름_토큰_반환() {
        // given
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(mock(RefreshToken.class)));

        // when
        RefreshToken result = refreshTokenService.getByUser(user);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    void deleteByUser_정상흐름_repository_위임() {
        // when
        refreshTokenService.deleteByUser(user);

        // then
        then(refreshTokenRepository).should().deleteByUser(user);
    }

    @Test
    void save_정상흐름_delete후_flush후_save_순서_검증() {
        // when
        refreshTokenService.save(user, "hashedToken", LocalDateTime.now(), LocalDateTime.now().plusDays(14));

        // then
        InOrder inOrder = inOrder(refreshTokenRepository);
        inOrder.verify(refreshTokenRepository).deleteByUser(user);
        inOrder.verify(refreshTokenRepository).flush();
        inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
