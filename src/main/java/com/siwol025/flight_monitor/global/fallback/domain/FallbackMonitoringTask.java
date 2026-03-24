package com.siwol025.flight_monitor.global.fallback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "fallback_monitoring_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FallbackMonitoringTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public FallbackMonitoringTask(String payload) {
        this.payload = payload;
        this.status = TaskStatus.PENDING;
    }

    public void markAsProcessed() {
        this.status = TaskStatus.PROCESSED;
    }

    public enum TaskStatus {
        PENDING, PROCESSED
    }
}
