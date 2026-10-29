package com.siwol025.flight_monitor.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 경합 테스트용 전용 DB: H2 기본 잠금 대기(2초)는 부하 시 짧아서, 잠금 시간 초과 대신 유일 제약 위반이 나도록 10초로 늘린다
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:route-service;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RouteService.class)
class RouteServiceTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @Autowired
    private RouteService routeService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void 노선찾기생성_정규화된키로_기존노선재사용() {
        Route existing = routeRepository.saveAndFlush(
                new Route(new RouteKey("ICN", "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY)));

        Route found = routeService.findOrCreate(new RouteKey(" icn", "nrt ", DEPARTURE_DATE, SeatGrade.ECONOMY));

        assertThat(found.getId()).isEqualTo(existing.getId());
        assertThat(routeRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 테스트 트랜잭션 밖에서 실제 커밋으로 경합을 재현한다
    @ExtendWith(OutputCaptureExtension.class)
    void 노선생성_동시중복_유일제약위반시_기존노선재사용(CapturedOutput output) throws Exception {
        RouteKey key = new RouteKey("ICN", "KIX", DEPARTURE_DATE, SeatGrade.BUSINESS);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commitA = new CountDownLatch(1);
        try {
            // A: 같은 노선을 insert하고 커밋하지 않은 채 기다린다(유일 키 잠금 보유)
            CompletableFuture<Long> a = CompletableFuture.supplyAsync(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        Route route = routeRepository.saveAndFlush(new Route(key));
                        inserted.countDown();
                        awaitLatch(commitA);
                        return route.getId();
                    }));
            assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();

            // B: 조회는 비어 있고(A 미커밋), insert가 A의 유일 키에서 기다린다.
            // A의 insert는 이미 끝났으므로 insert 문을 실행 중인 세션은 B뿐이다. B가 insert 안에 있으면 조회는 이미 비어서
            // 끝났으므로, 이때 A를 커밋하면 B는 반드시 유일 제약 위반 경로를 탄다(H2는 유일 인덱스 대기를 BLOCKER_ID로 보여 주지 않는다)
            CompletableFuture<Route> b = CompletableFuture.supplyAsync(() -> routeService.findOrCreate(key));
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE EXECUTING_STATEMENT LIKE 'insert into routes%'",
                    Integer.class) > 0);

            // A 커밋 → B는 유일 제약 위반 → 재조회로 A의 노선을 받는다
            commitA.countDown();

            assertThat(b.get(10, TimeUnit.SECONDS).getId()).isEqualTo(a.get(10, TimeUnit.SECONDS));
            assertThat(routeRepository.count()).isEqualTo(1);
            assertThat(output.getOut()).contains("같은 노선이 동시에 생성");
        } finally {
            routeRepository.deleteAll();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
