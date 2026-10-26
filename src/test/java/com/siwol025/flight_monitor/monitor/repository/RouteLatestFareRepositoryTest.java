package com.siwol025.flight_monitor.monitor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.monitor.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class RouteLatestFareRepositoryTest {

    @Autowired
    private RouteLatestFareRepository routeLatestFareRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void 최신최저가_금액소수둘째자리까지_저장됨() {
        // 금액 컬럼에 scale이 빠지면 생성 DDL이 scale 0이 되어 오류 없이 소수가 잘린다
        Route route = em.persist(new Route(new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY)));
        RouteLatestFare saved = routeLatestFareRepository.save(RouteLatestFare.builder()
                .route(route)
                .amount(new BigDecimal("45.67"))
                .currency("USD")
                .carrierCode("ZZ")
                .carrierName("Duffel Airways")
                .flightNumber("1079")
                .departingAt(LocalDateTime.of(2026, 11, 26, 19, 21))
                .arrivingAt(LocalDateTime.of(2026, 11, 26, 21, 36))
                .observedAt(Instant.parse("2026-10-26T00:00:00Z"))
                .build());
        em.flush();
        em.clear();

        assertThat(routeLatestFareRepository.findById(saved.getId()).orElseThrow().getAmount())
                .isEqualByComparingTo("45.67");
    }
}
