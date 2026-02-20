package com.siwol025.flight_monitor.monitor.domain;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String toEmail;
    private String subject;
    private String content;

    @Builder
    public NotificationHistory(String toEmail, String subject, String content) {
        this.toEmail = toEmail;
        this.subject = subject;
        this.content = content;
    }
}
