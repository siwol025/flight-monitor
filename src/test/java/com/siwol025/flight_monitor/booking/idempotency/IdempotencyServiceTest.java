package com.siwol025.flight_monitor.booking.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.KeyReused;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Processing;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.support.ComposeMysql;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리 API 멱등키(Idempotency-Key). 키를 한 번만 처리하고 결과를 저장해 같은 키 재요청에 그대로 돌려준다.
 * 키 선점은 유니크 제약, stale 판정은 DB NOW(6)라 compose와 같은 MySQL에서 검사한다. 서버 여러 대의 경합을 재현하려고
 * 테스트 트랜잭션 없이(각 단계가 바로 커밋) 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({IdempotencyService.class, JacksonConfig.class})
class IdempotencyServiceTest {

    private static final long USER_ID = 1L;
    private static final IdempotencyScope SCOPE = IdempotencyScope.BOOKING_CREATE;
    private static final String KEY = "6f1c2c1e-0d1b-4c1e-9a55-2b1f0f6a7c01";
    private static final String HASH = "a".repeat(64);

    record Body(long bookingId, String status) {
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyKeyRepository repository;

    @Autowired
    private IdempotencyProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final AtomicInteger processed = new AtomicInteger();

    @BeforeEach
    void emptyTable() {
        jdbc.update("DELETE FROM idempotency_keys");
    }

    @Test
    void idem_처음키_PROCESSING선점후_처리() {
        IdempotentResult<Body> result = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, claim -> {
            processed.incrementAndGet();
            // 처리하는 동안 키는 PROCESSING으로 선점돼 있다: 같은 키 재요청은 처리하지 않는다
            assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                    .isInstanceOf(Processing.class);
            assertThat(claim.bookingId()).isNull();
            return ResponseEntity.status(201).body(new Body(7L, "HELD"));
        });

        assertThat(processed).hasValue(1);
        assertThat(result).isInstanceOfSatisfying(Fresh.class, fresh -> {
            assertThat(fresh.response().getStatusCode().value()).isEqualTo(201);
            assertThat(fresh.response().getBody()).isEqualTo(new Body(7L, "HELD"));
        });
    }

    @Test
    void idem_완료된키_저장응답재생_처리안함() {
        idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(201).body(new Body(7L, "HELD")));

        IdempotentResult<Body> again = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected);

        assertThat(again).isEqualTo(new Replay<>(201, "{\"bookingId\":7,\"status\":\"HELD\"}"));
    }

    @Test
    void idem_실패결과도_재생() {
        idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(409).body(new Body(7L, "OFFER_UNAVAILABLE")));

        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isEqualTo(new Replay<>(409, "{\"bookingId\":7,\"status\":\"OFFER_UNAVAILABLE\"}"));
        assertThat(storedStatus()).isEqualTo("FAILED");
    }

    @Test
    void idem_202결과도_COMPLETED로재생() {
        // 외부 결과를 모르는 경우(hold·승인 불명확)도 202를 저장한다. 진행은 예매 조회로 본다(확인은 스케줄러)
        idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(202).body(new Body(7L, "HOLD_REQUESTED")));

        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isEqualTo(new Replay<>(202, "{\"bookingId\":7,\"status\":\"HOLD_REQUESTED\"}"));
        assertThat(storedStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void idem_처리중키_409() {
        processingKeyUpdatedSecondsAgo(10);

        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isInstanceOf(Processing.class);
    }

    @Test
    void idem_같은키다른본문_422() {
        idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(201).body(new Body(7L, "HELD")));

        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, "b".repeat(64), this::unexpected))
                .isInstanceOf(KeyReused.class);
    }

    @Test
    void idem_두서버동시같은키_한번만처리() throws Exception {
        // 같은 DB를 보는 두 서버(인스턴스). 먼저 선점한 쪽이 처리를 끝내지 않은 동안 다른 쪽이 같은 키를 받는다
        IdempotencyService serverA = idempotencyService;
        IdempotencyService serverB = new IdempotencyService(repository, properties, objectMapper);
        CountDownLatch release = new CountDownLatch(1);
        CyclicBarrier start = new CyclicBarrier(2);
        Function<IdempotencyClaim, ResponseEntity<Body>> slowWork = claim -> {
            processed.incrementAndGet();
            await(release);
            return ResponseEntity.status(201).body(new Body(7L, "HELD"));
        };

        CompletableFuture<IdempotentResult<Body>> a = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverA.execute(USER_ID, SCOPE, KEY, HASH, slowWork);
        });
        CompletableFuture<IdempotentResult<Body>> b = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverB.execute(USER_ID, SCOPE, KEY, HASH, slowWork);
        });

        // 진 쪽은 처리하지 않고 바로 돌아온다(이긴 쪽은 release 전까지 처리 중)
        IdempotentResult<Body> loser = CompletableFuture.anyOf(a, b).thenApply(r -> (IdempotentResult<Body>) r)
                .get(10, TimeUnit.SECONDS);
        assertThat(loser).isInstanceOf(Processing.class);
        release.countDown();

        assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                .filteredOn(Fresh.class::isInstance).hasSize(1);
        assertThat(processed).hasValue(1);
    }

    @Test
    void idem_PROCESSING이stale이면_재선점() {
        // 처리 서버가 예매를 만든 뒤 결과를 저장하기 전에 죽었다(2분 넘게 갱신 없음)
        processingKeyUpdatedSecondsAgo(10);
        long keyId = jdbc.queryForObject("SELECT id FROM idempotency_keys WHERE idem_key = ?", Long.class, KEY);
        idempotencyService.attachBooking(new IdempotencyClaim(keyId, null), 42L);
        // 아직 stale-after(2분) 전이면 처리 중이다
        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isInstanceOf(Processing.class);
        jdbc.update("UPDATE idempotency_keys SET updated_at = NOW(6) - INTERVAL 121 SECOND WHERE id = ?", keyId);

        IdempotentResult<Body> result = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, claim -> {
            processed.incrementAndGet();
            // 남긴 예매를 넘겨받아 외부 호출 없이 그 예매의 현재 상태를 응답할 수 있다
            assertThat(claim.bookingId()).isEqualTo(42L);
            // 재선점한 동안 다른 재요청은 처리하지 않는다
            assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                    .isInstanceOf(Processing.class);
            return ResponseEntity.status(202).body(new Body(42L, "HOLD_REQUESTED"));
        });

        assertThat(processed).hasValue(1);
        assertThat(result).isInstanceOf(Fresh.class);
        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isEqualTo(new Replay<>(202, "{\"bookingId\":42,\"status\":\"HOLD_REQUESTED\"}"));
    }

    @Test
    void idem_일시오류503_행삭제_같은키재처리가능() {
        IdempotentResult<Body> unavailable = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(503).build());
        assertThat(unavailable).isInstanceOf(Fresh.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_keys", Integer.class)).isZero();

        IdempotentResult<Body> retried = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, claim -> {
            processed.incrementAndGet();
            return ResponseEntity.status(201).body(new Body(7L, "HELD"));
        });

        assertThat(processed).hasValue(1);
        assertThat(retried).isInstanceOfSatisfying(Fresh.class,
                fresh -> assertThat(fresh.response().getStatusCode().value()).isEqualTo(201));
    }

    @Test
    void idem_500은_행유지_stale전409() {
        // 외부 호출 뒤의 500일 수 있다(결과 불명확). 키를 지우면 같은 키 재요청이 외부 호출을 다시 하므로 stale 재선점을 기다린다
        IdempotentResult<Body> failed = idempotencyService.execute(USER_ID, SCOPE, KEY, HASH,
                claim -> ResponseEntity.status(500).build());
        assertThat(failed).isInstanceOf(Fresh.class);

        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isInstanceOf(Processing.class);
        assertThat(storedStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void idem_처리중예외_PROCESSING유지() {
        assertThatThrownBy(() -> idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, claim -> {
            throw new IllegalStateException("처리 도중 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedStatus()).isEqualTo("PROCESSING");
        assertThat(idempotencyService.execute(USER_ID, SCOPE, KEY, HASH, this::unexpected))
                .isInstanceOf(Processing.class);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private <T> ResponseEntity<T> unexpected(IdempotencyClaim claim) {
        throw new AssertionError("처리하면 안 되는 요청을 처리했다");
    }

    private String storedStatus() {
        return jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE idem_key = ?", String.class, KEY);
    }

    // 다른 서버가 선점하고 처리 중인 키(updated_at은 DB 시각 기준 몇 초 전)
    private void processingKeyUpdatedSecondsAgo(int seconds) {
        jdbc.update("""
                INSERT INTO idempotency_keys (user_id, scope, idem_key, request_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PROCESSING', NOW(6) - INTERVAL ? SECOND, NOW(6) - INTERVAL ? SECOND)
                """, USER_ID, SCOPE.name(), KEY, HASH, seconds, seconds);
    }
}
