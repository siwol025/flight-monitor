package com.siwol025.flight_monitor.notification.worker;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentFormatterTest {

    private final NotificationContentFormatter formatter = new NotificationContentFormatter();

    @Test
    void createSubject_노선포함한_제목_반환() {
        // when
        String subject = formatter.createSubject(priceDrop());

        // then
        assertThat(subject).isEqualTo("✈️ ICN → NRT 2026-11-26 이코노미 최저가 하락 알림!");
    }

    @Test
    void createContent_가격변동정보_포함한_본문_반환() {
        // when
        String content = formatter.createContent(priceDrop());

        // then
        assertThat(content).isEqualTo("""
                노선: ICN → NRT (2026-11-26, 이코노미)
                최저가 항공편: ZZ1079 (출발 2026-11-26 19:21, 도착 2026-11-26 21:36, 현지 시각)
                가격 변동: 50.00 USD -> 45.67 USD""");
    }

    @Test
    void 메일문구_노선과최저가항공편과통화포함() {
        // when
        String mail = formatter.createSubject(priceDrop()) + "\n" + formatter.createContent(priceDrop());

        // then: 업체는 편명을 숫자만 주므로 항공사 코드를 붙여 보여 준다. 목표가는 표시 통화 기준이라 통화를 함께 쓴다
        assertThat(mail)
                .contains("ICN → NRT")
                .contains("2026-11-26")
                .contains("이코노미")
                .contains("ZZ1079")
                .contains("19:21")
                .contains("50.00 USD")
                .contains("45.67 USD");
    }

    private PriceDropNotificationDto priceDrop() {
        return PriceDropNotificationDto.builder()
                .routeId(7L)
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureDate(LocalDate.of(2026, 11, 26))
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("50.00"))
                .newPrice(new BigDecimal("45.67"))
                .currency("USD")
                .carrierCode("ZZ")
                .flightNumber("1079")
                .departingAt(LocalDateTime.of(2026, 11, 26, 19, 21))
                .arrivingAt(LocalDateTime.of(2026, 11, 26, 21, 36))
                .detectedAt(LocalDateTime.of(2026, 10, 26, 9, 1))
                .build();
    }
}
