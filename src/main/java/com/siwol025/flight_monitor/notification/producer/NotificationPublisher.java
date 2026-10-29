package com.siwol025.flight_monitor.notification.producer;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;

public interface NotificationPublisher {

    void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto);
}
