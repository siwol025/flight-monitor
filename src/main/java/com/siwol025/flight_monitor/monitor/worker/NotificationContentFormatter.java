package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import org.springframework.stereotype.Component;

@Component
public class NotificationContentFormatter {

    public String createSubject(String flightNumber) {
        return String.format("✈️ 항공편 ID: %s 가격 하락 알림!", flightNumber);
    }

    public String createContent(PriceDropNotificationDto dto) {
        return String.format(
                "항공편 ID: %s\n좌석: %s\n가격 변동: %s -> %s",
                dto.flightNumber(), dto.seatGrade(), dto.oldPrice(), dto.newPrice()
        );
    }
}
