package com.siwol025.flight_monitor.monitor.dto;

public record EmailSendTaskDto(
        String toEmail,
        String subject,
        String content
) {
}
