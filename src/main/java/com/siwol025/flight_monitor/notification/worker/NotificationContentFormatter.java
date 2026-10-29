package com.siwol025.flight_monitor.notification.worker;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 가격 하락 메일 문구. 이벤트가 만들어진 시점의 내용만으로 만든다(노선·최저가 항공편·통화).
 */
@Component
public class NotificationContentFormatter {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String createSubject(PriceDropNotificationDto dto) {
        return String.format("✈️ %s → %s %s %s 최저가 하락 알림!",
                dto.departureAirport(), dto.arrivalAirport(), dto.departureDate(), dto.seatGrade().getDescription());
    }

    public String createContent(PriceDropNotificationDto dto) {
        // 업체는 편명을 숫자만 준다(예: ZZ + 1079). 목표가는 표시 통화 기준이라 금액에 통화를 붙인다
        return String.format(
                "노선: %s → %s (%s, %s)\n최저가 항공편: %s%s (출발 %s, 도착 %s, 현지 시각)\n가격 변동: %s %s -> %s %s",
                dto.departureAirport(), dto.arrivalAirport(), dto.departureDate(), dto.seatGrade().getDescription(),
                dto.carrierCode(), dto.flightNumber(),
                dto.departingAt().format(LOCAL_TIME), dto.arrivingAt().format(LOCAL_TIME),
                dto.oldPrice().toPlainString(), dto.currency(), dto.newPrice().toPlainString(), dto.currency()
        );
    }
}
