package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteLatestFareRepository;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private RouteService routeService;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteLatestFareRepository routeLatestFareRepository;
    // 한국 시각 2026-10-26 08:00
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = mock(User.class);
    }

    // ─── subscribe ────────────────────────────────────────────────────────────

    @Test
    void subscribe_이미구독중이면_BadRequestException_DUPLICATE_SUBSCRIPTION() {
        given(testUser.getId()).willReturn(1L);
        Route route = mock(Route.class);
        given(route.getId()).willReturn(10L);
        given(routeService.findOrCreate(any(RouteKey.class))).willReturn(route);
        given(subscriptionRepository.existsByUserIdAndRouteId(1L, 10L))
                .willReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> subscriptionService.subscribe(testUser, "ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY, null, null)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.DUPLICATE_SUBSCRIPTION);
        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    // ─── unsubscribe ──────────────────────────────────────────────────────────

    @Test
    void unsubscribe_정상흐름_구독삭제됨() {
        Subscription subscription = mock(Subscription.class);
        given(subscriptionRepository.findByIdWithRoute(1L)).willReturn(Optional.of(subscription));
        doNothing().when(subscription).validateOwner(testUser);

        subscriptionService.unsubscribe(testUser, 1L);

        then(subscriptionRepository).should().delete(subscription);
    }

    @Test
    void unsubscribe_구독없으면_NotFoundException_SUBSCRIPTION_NOT_FOUND() {
        given(subscriptionRepository.findByIdWithRoute(99L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.unsubscribe(testUser, 99L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SUBSCRIPTION_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    // ─── getSubscription ──────────────────────────────────────────────────────

    @Test
    void getSubscription_구독없으면_NotFoundException_SUBSCRIPTION_NOT_FOUND() {
        given(subscriptionRepository.findByIdWithRoute(99L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.getSubscription(testUser, 99L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void getSubscription_타인구독이면_UnauthorizedException_UNAUTHORIZED_SUBSCRIPTION() {
        User other = mock(User.class);
        Subscription subscription = mock(Subscription.class);

        given(subscriptionRepository.findByIdWithRoute(1L)).willReturn(Optional.of(subscription));
        doThrow(new UnauthorizedException(ErrorTag.UNAUTHORIZED_SUBSCRIPTION)).when(subscription).validateOwner(other);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> subscriptionService.getSubscription(other, 1L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.UNAUTHORIZED_SUBSCRIPTION);
    }
}
