package com.siwol025.flight_monitor.route.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class RouteRepositoryTest {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void 노선_무효처리_상태INVALID() {
        Route route = routeRepository.save(
                new Route(new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY)));
        assertThat(route.getStatus()).isEqualTo(RouteStatus.ACTIVE);

        route.markInvalid();
        em.flush();
        em.clear();

        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.INVALID);
    }
}
