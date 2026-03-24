package com.siwol025.flight_monitor.global.outbox.repository;

import com.siwol025.flight_monitor.global.outbox.domain.Outbox;
import com.siwol025.flight_monitor.global.outbox.domain.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Outbox o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OutboxStatus status);

    List<Outbox> findByStatusAndCreatedAtBefore(OutboxStatus status, LocalDateTime createdAt);
}
