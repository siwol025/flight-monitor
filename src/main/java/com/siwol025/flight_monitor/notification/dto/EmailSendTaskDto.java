package com.siwol025.flight_monitor.notification.dto;

public record EmailSendTaskDto(
        String toEmail,
        String subject,
        String content
) {
}
