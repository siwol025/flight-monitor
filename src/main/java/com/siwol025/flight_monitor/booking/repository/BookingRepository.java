package com.siwol025.flight_monitor.booking.repository;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 같은 시각에 생성된 내역은 나중에 저장된 것(큰 ID)을 앞에 둔다
    List<Booking> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Booking> findByTossOrderId(String tossOrderId);

    // 상태 변경은 더티 체킹이 아니라 기대 상태를 조건으로 건 UPDATE다. 영향 행 0이면 다른 서버가 먼저 바꿨다.
    // 시각은 서버 시계가 아니라 DB NOW(6)로 남긴다(stale 판정 기준)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET status = :to, status_changed_at = NOW(6)
             WHERE id = :id AND status = :from
            """, nativeQuery = true)
    int transition(@Param("id") long id, @Param("from") String from, @Param("to") String to);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET status = 'PAYMENT_CONFIRMING', payment_key = :paymentKey, status_changed_at = NOW(6)
             WHERE id = :id AND status = 'HELD' AND toss_order_id = :orderId
            """, nativeQuery = true)
    int startConfirm(@Param("id") long id, @Param("orderId") String orderId, @Param("paymentKey") String paymentKey);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET toss_idempotency_key = :newKey, status_changed_at = NOW(6)
             WHERE id = :id AND status = 'PAYMENT_CONFIRMING' AND toss_idempotency_key = :oldKey
            """, nativeQuery = true)
    int reissueTossKey(@Param("id") long id, @Param("oldKey") String oldKey, @Param("newKey") String newKey);

    // 새 예매 행의 상태 시각을 DB 시각으로 남긴다(INSERT와 같은 트랜잭션에서 부른다)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE bookings SET status_changed_at = NOW(6) WHERE id = :id", nativeQuery = true)
    int stampStatusChangedAt(@Param("id") long id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings
               SET status = 'HELD', provider_order_id = :orderId, booking_reference = :bookingReference,
                   payment_required_by = :paymentRequiredBy, charge_amount = :chargeAmount,
                   toss_order_id = :tossOrderId, toss_idempotency_key = :tossKey, status_changed_at = NOW(6)
             WHERE id = :id AND status = 'HOLD_REQUESTED'
            """, nativeQuery = true)
    int hold(@Param("id") long id, @Param("orderId") String orderId,
             @Param("bookingReference") String bookingReference,
             @Param("paymentRequiredBy") LocalDateTime paymentRequiredBy, @Param("chargeAmount") long chargeAmount,
             @Param("tossOrderId") String tossOrderId, @Param("tossKey") String tossKey);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET status = 'FAILED', failure_code = :failureCode, status_changed_at = NOW(6)
             WHERE id = :id AND status = 'HOLD_REQUESTED'
            """, nativeQuery = true)
    int failHold(@Param("id") long id, @Param("failureCode") String failureCode);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET status = 'PAYMENT_APPROVED', toss_approved_at = :approvedAt, status_changed_at = NOW(6)
             WHERE id = :id AND status = 'PAYMENT_CONFIRMING'
            """, nativeQuery = true)
    int approvePayment(@Param("id") long id, @Param("approvedAt") LocalDateTime approvedAt);

    // 결제 확정 실패: 새 Toss 주문번호·새 Toss 키로 다시 결제할 수 있게 HELD로 돌린다(Toss 주문번호는 재사용하지 않는다)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings
               SET status = 'HELD', toss_order_id = :newOrderId, toss_idempotency_key = :newKey, payment_key = NULL,
                   status_changed_at = NOW(6)
             WHERE id = :id AND status = 'PAYMENT_CONFIRMING'
            """, nativeQuery = true)
    int reopenPayment(@Param("id") long id, @Param("newOrderId") String newOrderId, @Param("newKey") String newKey);

    // 발권 확인. 결제 결과를 모르는 채 보류된 예매도 조회로 결제·발권이 확인되면 여기로 끝낸다
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings
               SET status = 'TICKETED', duffel_paid_at = :paidAt, ticket_numbers = :ticketNumbers,
                   status_changed_at = NOW(6)
             WHERE id = :id AND status IN ('DUFFEL_PAYING', 'DUFFEL_PAYMENT_UNCONFIRMED')
            """, nativeQuery = true)
    int ticket(@Param("id") long id, @Param("paidAt") LocalDateTime paidAt,
               @Param("ticketNumbers") String ticketNumbers);

    // 결제가 확정 거절됐다(보상은 범위 밖). 상태는 DUFFEL_PAYING에 둔 채 원인 code만 남긴다
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET failure_code = :failureCode
             WHERE id = :id AND status = 'DUFFEL_PAYING'
            """, nativeQuery = true)
    int recordPaymentFailure(@Param("id") long id, @Param("failureCode") String failureCode);

    // 이 상태로 바뀐 지 olderThanSeconds초가 지난 예매. 시각은 서버 시계가 아니라 DB NOW(6)로 판정한다
    @Query(value = """
            SELECT id FROM bookings
             WHERE status = :status AND status_changed_at < NOW(6) - INTERVAL :olderThanSeconds SECOND
             ORDER BY status_changed_at
            """, nativeQuery = true)
    List<Long> findIdsInStatusSince(@Param("status") String status,
                                    @Param("olderThanSeconds") long olderThanSeconds);

    List<Booking> findAllByStatusOrderByStatusChangedAtAscIdAsc(BookingStatus status);
}
