package com.siwol025.flight_monitor.monitor.producer;

import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;

public interface NotificationPublisher {

    void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto);
}
