package com.siwol025.flight_monitor.booking.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등키 저장소. 앱 메모리에 키를 두지 않고, 선점은 유니크 제약(uk_idempotency_keys), 그 뒤 변경은 기대 상태를 조건으로 건
 * UPDATE로 한다. 시각은 서버 시계가 아니라 DB NOW(6)다(서버 간 시계 차이를 stale 판정에 섞지 않는다).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndScopeAndIdemKey(Long userId, IdempotencyScope scope, String idemKey);

    /** 처음 온 키를 PROCESSING으로 선점한다. 이미 있으면 유니크 위반(DataIntegrityViolationException)이다 */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (user_id, scope, idem_key, request_hash, status, created_at, updated_at)
            VALUES (:userId, :scope, :key, :hash, 'PROCESSING', NOW(6), NOW(6))
            """, nativeQuery = true)
    int insertProcessing(@Param("userId") long userId, @Param("scope") String scope, @Param("key") String key,
                         @Param("hash") String hash);

    /** 처리 서버가 멈춘(stale) PROCESSING 키의 처리권을 가져간다. 0행이면 아직 처리 중이거나 다른 서버가 먼저 가져갔다 */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE idempotency_keys SET updated_at = NOW(6)
             WHERE id = :id AND request_hash = :hash
               AND status = 'PROCESSING' AND updated_at < NOW(6) - INTERVAL :staleSeconds SECOND
            """, nativeQuery = true)
    int reclaimStale(@Param("id") long id, @Param("hash") String hash, @Param("staleSeconds") long staleSeconds);

    /** 이 요청이 만든 예매를 키에 남긴다. 예매 행 INSERT와 같은 트랜잭션에서 부른다 */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE idempotency_keys SET booking_id = :bookingId, updated_at = NOW(6)
             WHERE id = :id AND status = 'PROCESSING'
            """, nativeQuery = true)
    int attachBooking(@Param("id") long id, @Param("bookingId") long bookingId);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE idempotency_keys
               SET status = :status, response_status = :responseStatus, response_body = :responseBody, updated_at = NOW(6)
             WHERE id = :id AND status = 'PROCESSING'
            """, nativeQuery = true)
    int saveResult(@Param("id") long id, @Param("status") String status, @Param("responseStatus") int responseStatus,
                   @Param("responseBody") String responseBody);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE id = :id AND status = 'PROCESSING'", nativeQuery = true)
    int deleteProcessing(@Param("id") long id);
}
