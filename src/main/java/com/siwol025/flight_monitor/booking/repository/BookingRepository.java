package com.siwol025.flight_monitor.booking.repository;

import com.siwol025.flight_monitor.booking.domain.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 같은 시각에 생성된 내역은 나중에 저장된 것(큰 ID)을 앞에 둔다
    List<Booking> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

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
}
