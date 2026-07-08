package com.siwol025.flight_monitor.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private User owner;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        owner = mock(User.class);
        Flight flight = mock(Flight.class);
        subscription = Subscription.builder()
                .user(owner)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .build();
    }

    @Test
    void validateOwner_소유자이면_예외없음() {
        // owner 참조 동일 → User.equals()에서 this == o로 통과
        assertDoesNotThrow(() -> subscription.validateOwner(owner));
    }

    @Test
    void builder_조건없이_생성시_두_조건_필드가_null이다() {
        assertThat(subscription.getTargetPrice()).isNull();
        assertThat(subscription.getDropThresholdPercent()).isNull();
    }

    @Test
    void builder_targetPrice_지정시_저장된다() {
        Flight flight = mock(Flight.class);
        BigDecimal targetPrice = BigDecimal.valueOf(120_000);

        Subscription sub = Subscription.builder()
                .user(owner)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .targetPrice(targetPrice)
                .build();

        assertThat(sub.getTargetPrice()).isEqualByComparingTo(targetPrice);
    }

    @Test
    void builder_dropThresholdPercent_지정시_저장된다() {
        Flight flight = mock(Flight.class);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(10.00);

        Subscription sub = Subscription.builder()
                .user(owner)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .dropThresholdPercent(dropThresholdPercent)
                .build();

        assertThat(sub.getDropThresholdPercent()).isEqualByComparingTo(dropThresholdPercent);
    }

    @Test
    void validateOwner_다른사용자이면_UnauthorizedException발생() {
        User other = mock(User.class); // 다른 참조 = 다른 사용자

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> subscription.validateOwner(other)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.UNAUTHORIZED_SUBSCRIPTION);
        assertThat(ex.getStatus().value()).isEqualTo(401);
    }
}
