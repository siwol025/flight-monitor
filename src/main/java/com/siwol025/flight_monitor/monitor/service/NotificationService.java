package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;

public interface NotificationService {

    void sendPriceDropNotification(EmailSendTaskDto taskDto);
}
