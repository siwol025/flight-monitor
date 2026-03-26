package com.siwol025.flight_monitor.global.fallback.repository;

import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FallbackMonitoringTaskRepository extends JpaRepository<FallbackMonitoringTask, Long> {
    @Query(value = "SELECT * FROM fallback_monitoring_tasks WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<FallbackMonitoringTask> findPendingTaskForUpdate();

    @Query(value = "SELECT GET_LOCK(:key, :timeout)", nativeQuery = true)
    Integer getLock(@Param("key") String key, @Param("timeout") int timeout);

    @Query(value = "SELECT RELEASE_LOCK(:key)", nativeQuery = true)
    Integer releaseLock(@Param("key") String key);
}
