package com.siwol025.flight_monitor.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SubscriptionRepositoryTest {

    // 노선 출발일 비교의 기준일(호출자가 한국 날짜로 넘긴다)
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    TestEntityManager em;

    @Test
    void findActiveRouteIds_ACTIVE상태만_반환() {
        // given
        User user = em.persist(User.builder()
                .email("test@example.com")
                .name("테스터")
                .provider(Provider.GOOGLE)
                .providerId("google-123")
                .role(Role.USER)
                .build());

        Route economy = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.plusDays(1), SeatGrade.ECONOMY)));
        Route business = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.plusDays(1), SeatGrade.BUSINESS)));

        Subscription activeSub = Subscription.builder()
                .user(user)
                .route(economy)
                .price(BigDecimal.valueOf(300000))
                .build();
        em.persist(activeSub);

        Subscription inactiveSub = Subscription.builder()
                .user(user)
                .route(business)
                .price(BigDecimal.valueOf(700000))
                .build();
        inactiveSub.updateStatus(SubscriptionStatus.INACTIVE);
        em.persist(inactiveSub);

        em.flush();
        em.clear();

        // when
        List<Long> result =
                subscriptionRepository.findActiveRouteIds(SubscriptionStatus.ACTIVE, RouteStatus.ACTIVE, TODAY);

        // then
        assertThat(result).containsExactly(economy.getId());
    }

    @Test
    void findSubscribersWithCondition_조건지정구독자_전체필드_반환됨() {
        User user = em.persist(User.builder()
                .email("cond@example.com").name("조건유저")
                .provider(Provider.GOOGLE).providerId("google-cond-1").role(Role.USER).build());
        Route route = em.persist(new Route(new RouteKey("ICN", "LAX", TODAY.plusDays(1), SeatGrade.ECONOMY)));
        em.persist(Subscription.builder()
                .user(user).route(route)
                .price(BigDecimal.valueOf(800000))
                .targetPrice(BigDecimal.valueOf(120000))
                .dropThresholdPercent(BigDecimal.valueOf(10.00)).build());
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithCondition(route.getId(), SubscriptionStatus.ACTIVE);

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
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.plusDays(2), SeatGrade.ECONOMY)));
        em.persist(Subscription.builder()
                .user(user).route(route)
                .price(BigDecimal.valueOf(300000)).build());
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithCondition(route.getId(), SubscriptionStatus.ACTIVE);

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
        Route route = em.persist(new Route(new RouteKey("ICN", "SYD", TODAY.plusDays(3), SeatGrade.ECONOMY)));
        Subscription inactive = Subscription.builder()
                .user(user).route(route)
                .price(BigDecimal.valueOf(500000)).build();
        inactive.updateStatus(SubscriptionStatus.INACTIVE);
        em.persist(inactive);
        em.flush(); em.clear();

        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithCondition(route.getId(), SubscriptionStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    void findSubscribersWithCondition_다른좌석등급_구독자_제외됨() {
        // given: 같은 구간·날짜에 이코노미 노선 구독자와 비즈니스 노선 구독자가 있다(좌석등급이 다르면 다른 노선)
        User economyUser = em.persist(User.builder()
                .email("economy@example.com").name("이코노미유저")
                .provider(Provider.GOOGLE).providerId("google-eco-1").role(Role.USER).build());
        User businessUser = em.persist(User.builder()
                .email("business@example.com").name("비즈니스유저")
                .provider(Provider.GOOGLE).providerId("google-biz-1").role(Role.USER).build());
        Route economy = em.persist(new Route(new RouteKey("ICN", "CDG", TODAY.plusDays(4), SeatGrade.ECONOMY)));
        Route business = em.persist(new Route(new RouteKey("ICN", "CDG", TODAY.plusDays(4), SeatGrade.BUSINESS)));
        em.persist(Subscription.builder()
                .user(economyUser).route(economy)
                .price(BigDecimal.valueOf(300000)).build());
        em.persist(Subscription.builder()
                .user(businessUser).route(business)
                .price(BigDecimal.valueOf(1500000))
                .targetPrice(BigDecimal.valueOf(1000000)).build());
        em.flush(); em.clear();

        // when: 이코노미 노선 가격 하락 이벤트의 구독자 조회
        List<SubscriberWithConditionDto> result =
                subscriptionRepository.findSubscribersWithCondition(economy.getId(), SubscriptionStatus.ACTIVE);

        // then: 비즈니스 구독자는 포함되지 않는다
        assertThat(result).extracting(SubscriberWithConditionDto::email)
                .containsExactly("economy@example.com");
    }

    @Test
    void bulkExpireSubscriptions_출발완료_ACTIVE구독_EXPIRED로_변경되고_건수반환됨() {
        // given
        User user = em.persist(User.builder()
                .email("bulk@example.com")
                .name("벌크유저")
                .provider(Provider.GOOGLE)
                .providerId("google-bulk-1")
                .role(Role.USER)
                .build());

        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.minusDays(1), SeatGrade.ECONOMY)));

        Subscription activeSub = Subscription.builder()
                .user(user)
                .route(route)
                .price(BigDecimal.valueOf(300000))
                .build();
        em.persist(activeSub);

        em.flush();
        em.clear();

        // when
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, TODAY);

        // then
        assertThat(updated).isEqualTo(1);
        Subscription found = subscriptionRepository.findById(activeSub.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void bulkExpireSubscriptions_출발전_ACTIVE구독_변경안됨() {
        // given
        User user = em.persist(User.builder()
                .email("bulk2@example.com")
                .name("벌크유저2")
                .provider(Provider.GOOGLE)
                .providerId("google-bulk-2")
                .role(Role.USER)
                .build());

        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.plusDays(1), SeatGrade.ECONOMY)));

        Subscription activeSub = Subscription.builder()
                .user(user)
                .route(route)
                .price(BigDecimal.valueOf(300000))
                .build();
        em.persist(activeSub);

        em.flush();
        em.clear();

        // when
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, TODAY);

        // then
        assertThat(updated).isEqualTo(0);
        Subscription found = subscriptionRepository.findById(activeSub.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void bulkExpireSubscriptions_이미EXPIRED구독_변경안됨() {
        // given
        User user = em.persist(User.builder()
                .email("bulk3@example.com")
                .name("벌크유저3")
                .provider(Provider.GOOGLE)
                .providerId("google-bulk-3")
                .role(Role.USER)
                .build());

        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", TODAY.minusDays(1), SeatGrade.ECONOMY)));

        Subscription expiredSub = Subscription.builder()
                .user(user)
                .route(route)
                .price(BigDecimal.valueOf(300000))
                .build();
        expiredSub.updateStatus(SubscriptionStatus.EXPIRED);
        em.persist(expiredSub);

        em.flush();
        em.clear();

        // when
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, TODAY);

        // then
        assertThat(updated).isEqualTo(0);
        Subscription found = subscriptionRepository.findById(expiredSub.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
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

        Route route = em.persist(new Route(new RouteKey("GMP", "CJU", TODAY.plusDays(2), SeatGrade.ECONOMY)));

        Subscription subscription = Subscription.builder()
                .user(user)
                .route(route)
                .price(BigDecimal.valueOf(150000))
                .build();
        em.persist(subscription);

        em.flush();
        em.clear();

        // when
        List<Subscription> result = subscriptionRepository.findAllByUserId(user.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoute()).isNotNull();
    }
}
