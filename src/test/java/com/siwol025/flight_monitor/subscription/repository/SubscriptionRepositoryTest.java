package com.siwol025.flight_monitor.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
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
    void findSubscribersWithCondition_조건지정구독자_전체필드_반환됨() {
        User user = em.persist(User.builder()
                .email("cond@example.com").name("조건유저")
                .provider(Provider.GOOGLE).providerId("google-cond-1").role(Role.USER).build());
        Flight flight = em.persist(Flight.builder()
                .flightId(3001L).flightNumber("KE301").airlineCode("KE")
                .departureAirport("ICN").arrivalAirport("LAX")
                .departureTime(LocalDateTime.now().plusDays(1))
                .arrivalTime(LocalDateTime.now().plusDays(1).plusHours(11)).build());
        em.persist(Subscription.builder()
                .user(user).flight(flight).seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(800000))
                .targetPrice(BigDecimal.valueOf(120000))
                .dropThresholdPercent(BigDecimal.valueOf(10.00)).build());
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithConditionByFlightId(3001L, SubscriptionStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("cond@example.com");
        assertThat(result.get(0).targetPrice()).isEqualByComparingTo(BigDecimal.valueOf(120000));
        assertThat(result.get(0).dropThresholdPercent()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
    }

    @Test
    void findSubscribersWithCondition_조건없는구독자_null필드_반환됨() {
        User user = em.persist(User.builder()
                .email("nocond@example.com").name("무조건유저")
                .provider(Provider.GOOGLE).providerId("google-nocond-1").role(Role.USER).build());
        Flight flight = em.persist(Flight.builder()
                .flightId(3002L).flightNumber("KE302").airlineCode("KE")
                .departureAirport("ICN").arrivalAirport("NRT")
                .departureTime(LocalDateTime.now().plusDays(2))
                .arrivalTime(LocalDateTime.now().plusDays(2).plusHours(2)).build());
        em.persist(Subscription.builder()
                .user(user).flight(flight).seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(300000)).build());
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithConditionByFlightId(3002L, SubscriptionStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("nocond@example.com");
        assertThat(result.get(0).targetPrice()).isNull();
        assertThat(result.get(0).dropThresholdPercent()).isNull();
    }

    @Test
    void findSubscribersWithCondition_ACTIVE아닌구독_제외됨() {
        User user = em.persist(User.builder()
                .email("inactive@example.com").name("비활성유저")
                .provider(Provider.GOOGLE).providerId("google-inactive-1").role(Role.USER).build());
        Flight flight = em.persist(Flight.builder()
                .flightId(3003L).flightNumber("KE303").airlineCode("KE")
                .departureAirport("ICN").arrivalAirport("SYD")
                .departureTime(LocalDateTime.now().plusDays(3))
                .arrivalTime(LocalDateTime.now().plusDays(3).plusHours(10)).build());
        Subscription inactive = Subscription.builder()
                .user(user).flight(flight).seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(500000)).build();
        inactive.updateStatus(SubscriptionStatus.INACTIVE);
        em.persist(inactive);
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithConditionByFlightId(3003L, SubscriptionStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void findExpirableSubscriptions_출발완료_ACTIVE구독_반환됨() {
        // given
        User user = em.persist(User.builder()
                .email("expirable@example.com")
                .name("만료유저")
                .provider(Provider.GOOGLE)
                .providerId("google-expirable-1")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(9001L)
                .flightNumber("KE901")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(LocalDateTime.now().minusDays(1))
                .arrivalTime(LocalDateTime.now().minusDays(1).plusHours(3))
                .build());

        Subscription activeSub = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(300000))
                .build();
        em.persist(activeSub);

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlight()).isNotNull();
    }

    @Test
    void findExpirableSubscriptions_출발전_ACTIVE구독_제외됨() {
        // given
        User user = em.persist(User.builder()
                .email("future@example.com")
                .name("미래유저")
                .provider(Provider.GOOGLE)
                .providerId("google-future-1")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(9002L)
                .flightNumber("KE902")
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

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findExpirableSubscriptions_INACTIVE구독_제외됨() {
        // given
        User user = em.persist(User.builder()
                .email("inactive2@example.com")
                .name("비활성유저2")
                .provider(Provider.GOOGLE)
                .providerId("google-inactive-2")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(9003L)
                .flightNumber("KE903")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(LocalDateTime.now().minusDays(1))
                .arrivalTime(LocalDateTime.now().minusDays(1).plusHours(3))
                .build());

        Subscription inactiveSub = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(300000))
                .build();
        inactiveSub.updateStatus(SubscriptionStatus.INACTIVE);
        em.persist(inactiveSub);

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findExpirableSubscriptions_이미EXPIRED구독_제외됨() {
        // given
        User user = em.persist(User.builder()
                .email("expired@example.com")
                .name("만료됨유저")
                .provider(Provider.GOOGLE)
                .providerId("google-expired-1")
                .role(Role.USER)
                .build());

        Flight flight = em.persist(Flight.builder()
                .flightId(9004L)
                .flightNumber("KE904")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(LocalDateTime.now().minusDays(1))
                .arrivalTime(LocalDateTime.now().minusDays(1).plusHours(3))
                .build());

        Subscription expiredSub = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(300000))
                .build();
        expiredSub.updateStatus(SubscriptionStatus.EXPIRED);
        em.persist(expiredSub);

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findExpirableSubscriptions(SubscriptionStatus.ACTIVE);

        // then
        assertThat(result).isEmpty();
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
