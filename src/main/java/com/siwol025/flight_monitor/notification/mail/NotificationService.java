package com.siwol025.flight_monitor.notification.mail;

import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;

public interface NotificationService {

    void sendPriceDropNotification(EmailSendTaskDto taskDto);
}
