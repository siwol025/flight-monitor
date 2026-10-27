package com.siwol025.flight_monitor.subscription.repository;

import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query(
            "SELECT s FROM Subscription s " +
            "JOIN FETCH s.route " +
            "WHERE s.user.id = :userId " +
            "ORDER BY s.createdAt desc"
    )
    List<Subscription> findAllByUserId(Long userId);

    @Query(
            "SELECT s " +
            "FROM Subscription s " +
            "JOIN FETCH s.route " +
            "WHERE s.id = :id"
    )
    Optional<Subscription> findByIdWithRoute(@Param("id") Long id);

    boolean existsByUserIdAndRouteId(Long userId, Long routeId);

    @Query(
            "SELECT DISTINCT r.id " +
            "FROM Subscription s " +
            "JOIN s.route r " +
            "WHERE s.status = :status " +
            "AND r.status = :routeStatus " +
            "AND r.departureDate >= :today"
    )
    List<Long> findActiveRouteIds(@Param("status") SubscriptionStatus status,
                                  @Param("routeStatus") RouteStatus routeStatus,
                                  @Param("today") LocalDate today);

    @Query(
            "SELECT DISTINCT " +
                "new com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto(s.user.email, s.targetPrice, s.dropThresholdPercent) " +
            "FROM Subscription s " +
            "WHERE s.route.id = :routeId " +
            "AND s.status = :status"
    )
    List<SubscriberWithConditionDto> findSubscribersWithCondition(@Param("routeId") Long routeId,
                                                                  @Param("status") SubscriptionStatus status);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Subscription s SET s.status = :expired WHERE s.status = :active AND s.route.departureDate < :today")
    int bulkExpireSubscriptions(@Param("active") SubscriptionStatus active,
                                @Param("expired") SubscriptionStatus expired,
                                @Param("today") LocalDate today);

    // 대부분 0행이다. 같은 영속성 컨텍스트의 구독이 옛 값(null)을 보지 않게 실행 전 flush, 실행 후 clear한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Subscription s SET s.price = :price WHERE s.route.id = :routeId AND s.price IS NULL")
    int fillPendingBaselinePrice(@Param("routeId") Long routeId, @Param("price") BigDecimal price);
}
