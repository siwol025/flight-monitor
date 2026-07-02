package com.siwol025.flight_monitor.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SubscriptionRepositoryTest {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    TestEntityManager em;

    @Test
    void findActiveFlightIdsAndSeatGrade_ACTIVE상태만_반환() {
        // given
        User user = em.persist(User.builder()
                .email("test@example.com")
                .name("테스터")
                .provider(Provider.GOOGLE)
                .providerId("google-123")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(1001L)
                .flightNumber("KE001")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(3))
                .build());

        Subscription activeSub = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(300000))
                .build();
        em.persist(activeSub);

        Subscription inactiveSub = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.BUSINESS)
                .price(BigDecimal.valueOf(700000))
                .build();
        inactiveSub.updateStatus(SubscriptionStatus.INACTIVE);
        em.persist(inactiveSub);

        em.flush();
        em.clear();

        // when
        List<FlightMonitorTaskDto> result =
                subscriptionRepository.findActiveFlightIdsAndSeatGrade(SubscriptionStatus.ACTIVE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).seatGrade()).isEqualTo(SeatGrade.ECONOMY);
    }

    @Test
    void findAllByUserId_JOIN_FETCH_정상조회() {
        // given
        User user = em.persist(User.builder()
                .email("fetch@example.com")
                .name("페치유저")
                .provider(Provider.GOOGLE)
                .providerId("google-456")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(2001L)
                .flightNumber("OZ201")
                .airlineCode("OZ")
                .departureAirport("GMP")
                .arrivalAirport("CJU")
                .departureTime(LocalDateTime.now().plusDays(2))
                .arrivalTime(LocalDateTime.now().plusDays(2).plusHours(1))
                .build());

        Subscription subscription = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150000))
                .build();
        em.persist(subscription);

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findAllByUserId(user.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlight()).isNotNull();
    }
}
