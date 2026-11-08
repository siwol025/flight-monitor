package com.siwol025.flight_monitor.booking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.support.ComposeMysql;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예매 상태의 조건부 UPDATE. NOW(6)·ascii_bin 등 MySQL 문법이라 compose와 같은 MySQL에서 Flyway 스키마로 검사한다.
 * 서버 여러 대의 경합을 재현하려고 테스트 트랜잭션 없이(각 갱신이 바로 커밋) 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(BookingStateWriter.class)
class BookingRepositoryConcurrencyTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
    }

    @Autowired
    private BookingStateWriter writer;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.update("DELETE FROM bookings");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users (id, email, provider, provider_id, user_role) "
                + "VALUES (1, 'booker@test.com', 'GOOGLE', 'g1', 'USER')");
    }

    @Test
    void transition_기대상태일때만_1행() {
        long id = booking(BookingStatus.HOLD_REQUESTED, "fm-order-1", "toss-key-1");

        assertThat(writer.transition(id, BookingStatus.HELD, BookingStatus.PAYMENT_CONFIRMING)).isFalse();
        assertThat(status(id)).isEqualTo(BookingStatus.HOLD_REQUESTED);

        assertThat(writer.transition(id, BookingStatus.HOLD_REQUESTED, BookingStatus.HELD)).isTrue();
        assertThat(status(id)).isEqualTo(BookingStatus.HELD);
        assertThat(bookingRepository.findById(id).orElseThrow().getStatusChangedAt()).isNotNull();
    }

    @Test
    void transition_두서버동시_한건만성공() throws Exception {
        long id = booking(BookingStatus.PAYMENT_APPROVED, "fm-order-1", "toss-key-1");
        // 같은 DB를 보는 두 서버(인스턴스)가 같은 예매를 동시에 가져가려 한다
        BookingStateWriter serverA = writer;
        BookingStateWriter serverB = new BookingStateWriter(bookingRepository);
        CyclicBarrier start = new CyclicBarrier(2);

        List<CompletableFuture<Boolean>> claims = List.of(serverA, serverB).stream()
                .map(server -> CompletableFuture.<Boolean>supplyAsync(() -> {
                    await(start);
                    return server.transition(id, BookingStatus.PAYMENT_APPROVED, BookingStatus.DUFFEL_PAYING);
                }))
                .toList();

        assertThat(claims.stream().map(CompletableFuture::join)).containsExactlyInAnyOrder(true, false);
        assertThat(status(id)).isEqualTo(BookingStatus.DUFFEL_PAYING);
    }

    @Test
    void startConfirm_HELD이고_Toss주문번호일치때만_1행() {
        long id = booking(BookingStatus.HELD, "fm-order-1", "toss-key-1");

        assertThat(writer.startConfirm(id, "fm-order-other", "pay_1")).isFalse();
        assertThat(status(id)).isEqualTo(BookingStatus.HELD);

        assertThat(writer.startConfirm(id, "fm-order-1", "pay_1")).isTrue();
        assertThat(status(id)).isEqualTo(BookingStatus.PAYMENT_CONFIRMING);
        assertThat(bookingRepository.findById(id).orElseThrow().getPaymentKey()).isEqualTo("pay_1");

        // 이미 승인 중이면 두 번째 요청은 Toss를 부르지 못한다
        assertThat(writer.startConfirm(id, "fm-order-1", "pay_1")).isFalse();
    }

    @Test
    void reissueTossKey_옛키일때만_1행() {
        long id = booking(BookingStatus.PAYMENT_CONFIRMING, "fm-order-1", "toss-key-1");

        assertThat(writer.reissueTossKey(id, "toss-key-1", "toss-key-2")).isTrue();
        // 다른 서버가 같은 옛 키로 또 바꾸려 하면 0행(새 키는 한 서버만 정한다)
        assertThat(writer.reissueTossKey(id, "toss-key-1", "toss-key-3")).isFalse();

        assertThat(bookingRepository.findById(id).orElseThrow().getTossIdempotencyKey()).isEqualTo("toss-key-2");
    }

    private long booking(BookingStatus status, String tossOrderId, String tossKey) {
        jdbc.update("""
                INSERT INTO bookings (user_id, offer_id, departure_airport, arrival_airport, departure_date, seat_grade,
                    carrier_code, carrier_name, flight_number, departing_at, arriving_at, amount, currency,
                    passenger_title, given_name, family_name, passenger_gender, born_on, email, phone_number,
                    created_at, status, status_changed_at, toss_order_id, toss_idempotency_key)
                VALUES (1, 'off_1', 'ICN', 'NRT', '2026-11-26', 'ECONOMY', 'ZZ', 'Duffel Airways', '1001',
                    '2026-11-26 09:00:00', '2026-11-26 11:20:00', 250000.00, 'USD', 'MR', 'Gildong', 'Hong', 'M',
                    '1990-01-01', 'p@test.com', '+821012345678', NOW(6), ?, NOW(6), ?, ?)
                """, status.name(), tossOrderId, tossKey);
        return jdbc.queryForObject("SELECT id FROM bookings WHERE toss_order_id = ?", Long.class, tossOrderId);
    }

    private BookingStatus status(long id) {
        return bookingRepository.findById(id).orElseThrow().getStatus();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
