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
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private FlightService flightService;
    @Mock private FlightDataProvider flightDataProvider;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = mock(User.class);
    }

    // ─── subscribe ────────────────────────────────────────────────────────────

    @Test
    void subscribe_정상흐름_구독저장됨() {
        given(testUser.getId()).willReturn(1L);
        given(subscriptionRepository.existsByUserIdAndFlightIdAndSeatGrade(1L, 10L, SeatGrade.ECONOMY))
                .willReturn(false);

        MockFlightResponse response = flightResponseWithSeatPrices(
                new MockFlightSeatPriceResponse("KE101", SeatGrade.ECONOMY, BigDecimal.valueOf(300_000))
        );
        Flight flight = mock(Flight.class);

        given(flightDataProvider.fetchMockFlight(10L)).willReturn(response);
        given(flightService.findOrCreateFlight(response)).willReturn(flight);

        subscriptionService.subscribe(testUser, 10L, SeatGrade.ECONOMY, null, null);

        then(subscriptionRepository).should().save(any(Subscription.class));
    }

    @Test
    void subscribe_이미구독중이면_BadRequestException_DUPLICATE_SUBSCRIPTION() {
        given(testUser.getId()).willReturn(1L);
        given(subscriptionRepository.existsByUserIdAndFlightIdAndSeatGrade(1L, 10L, SeatGrade.ECONOMY))
                .willReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> subscriptionService.subscribe(testUser, 10L, SeatGrade.ECONOMY, null, null)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.DUPLICATE_SUBSCRIPTION);
        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    void subscribe_좌석등급가격없으면_NotFoundException_SEAT_PRICE_NOT_FOUND() {
        given(testUser.getId()).willReturn(1L);
        given(subscriptionRepository.existsByUserIdAndFlightIdAndSeatGrade(1L, 10L, SeatGrade.ECONOMY))
                .willReturn(false);

        // ECONOMY 가격 없이 BUSINESS만 포함한 응답
        MockFlightResponse response = flightResponseWithSeatPrices(
                new MockFlightSeatPriceResponse("KE101", SeatGrade.BUSINESS, BigDecimal.valueOf(500_000))
        );
        Flight flight = mock(Flight.class);

        given(flightDataProvider.fetchMockFlight(10L)).willReturn(response);
        given(flightService.findOrCreateFlight(response)).willReturn(flight);

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.subscribe(testUser, 10L, SeatGrade.ECONOMY, null, null)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SEAT_PRICE_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    // ─── unsubscribe ──────────────────────────────────────────────────────────

    @Test
    void unsubscribe_정상흐름_구독삭제됨() {
        Subscription subscription = mock(Subscription.class);
        given(subscriptionRepository.findByIdWithFlight(1L)).willReturn(Optional.of(subscription));
        doNothing().when(subscription).validateOwner(testUser);

        subscriptionService.unsubscribe(testUser, 1L);

        then(subscriptionRepository).should().delete(subscription);
    }

    @Test
    void unsubscribe_구독없으면_NotFoundException_SUBSCRIPTION_NOT_FOUND() {
        given(subscriptionRepository.findByIdWithFlight(99L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.unsubscribe(testUser, 99L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SUBSCRIPTION_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    // ─── getSubscription ──────────────────────────────────────────────────────

    @Test
    void getSubscription_정상흐름_응답DTO반환됨() {
        Subscription subscription = mock(Subscription.class);
        Flight flight = mock(Flight.class);
        BigDecimal subscribedPrice = BigDecimal.valueOf(300_000);
        BigDecimal currentPrice = BigDecimal.valueOf(250_000);

        given(subscriptionRepository.findByIdWithFlight(1L)).willReturn(Optional.of(subscription));
        doNothing().when(subscription).validateOwner(testUser);
        given(subscription.getFlight()).willReturn(flight);
        given(flight.getId()).willReturn(10L);
        given(subscription.getSeatGrade()).willReturn(SeatGrade.ECONOMY);
        given(subscription.getPrice()).willReturn(subscribedPrice);
        given(flightDataProvider.fetchMockFlight(10L)).willReturn(
                flightResponseWithSeatPrices(
                        new MockFlightSeatPriceResponse("KE101", SeatGrade.ECONOMY, currentPrice)
                )
        );

        SubscriptionDetailResponse result = subscriptionService.getSubscription(testUser, 1L);

        assertThat(result.currentPrice()).isEqualByComparingTo(currentPrice);
        assertThat(result.priceDifference()).isEqualByComparingTo(BigDecimal.valueOf(-50_000));
    }

    @Test
    void getSubscription_구독없으면_NotFoundException_SUBSCRIPTION_NOT_FOUND() {
        given(subscriptionRepository.findByIdWithFlight(99L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.getSubscription(testUser, 99L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    void getSubscription_현재가격없으면_NotFoundException_SEAT_PRICE_NOT_FOUND() {
        Subscription subscription = mock(Subscription.class);
        Flight flight = mock(Flight.class);

        given(subscription.getFlight()).willReturn(flight);
        given(flight.getId()).willReturn(10L);
        given(subscription.getSeatGrade()).willReturn(SeatGrade.ECONOMY);
        given(subscriptionRepository.findByIdWithFlight(1L)).willReturn(Optional.of(subscription));
        doNothing().when(subscription).validateOwner(testUser);

        // 외부 API가 ECONOMY 가격 없이 응답
        MockFlightResponse response = flightResponseWithSeatPrices(
                new MockFlightSeatPriceResponse("KE101", SeatGrade.BUSINESS, BigDecimal.valueOf(500_000))
        );
        given(flightDataProvider.fetchMockFlight(10L)).willReturn(response);

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> subscriptionService.getSubscription(testUser, 1L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SEAT_PRICE_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void getSubscription_타인구독이면_UnauthorizedException_UNAUTHORIZED_SUBSCRIPTION() {
        User other = mock(User.class);
        Subscription subscription = mock(Subscription.class);

        given(subscriptionRepository.findByIdWithFlight(1L)).willReturn(Optional.of(subscription));
        doThrow(new UnauthorizedException(ErrorTag.UNAUTHORIZED_SUBSCRIPTION)).when(subscription).validateOwner(other);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> subscriptionService.getSubscription(other, 1L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.UNAUTHORIZED_SUBSCRIPTION);
    }

    // ─── 구조 테스트 ──────────────────────────────────────────────────────────────

    @Test
    void SubscriptionService는_FlightDataProvider_인터페이스에_의존해야한다() {
        boolean hasFlightDataProviderField = java.util.Arrays.stream(SubscriptionService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(FlightDataProvider.class));

        assertThat(hasFlightDataProviderField).isTrue();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private MockFlightResponse flightResponseWithSeatPrices(MockFlightSeatPriceResponse... prices) {
        return MockFlightResponse.builder()
                .id(10L)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .departureTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .arrivalTime(LocalDateTime.of(2026, 7, 1, 12, 0))
                .isSeatAvailable(true)
                .seatPrices(List.of(prices))
                .build();
    }
}
