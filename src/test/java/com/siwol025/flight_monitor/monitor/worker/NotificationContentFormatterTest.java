package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentFormatterTest {

    private final NotificationContentFormatter formatter = new NotificationContentFormatter();

    @Test
    void createSubject_항공편번호_포함한_제목_반환() {
        // given
        String flightNumber = "ABC123";

        // when
        String subject = formatter.createSubject(flightNumber);

        // then
        assertThat(subject).isEqualTo("✈️ 항공편 ID: ABC123 가격 하락 알림!");
    }

    @Test
    void createContent_가격변동정보_포함한_본문_반환() {
        // given
        PriceDropNotificationDto dto = PriceDropNotificationDto.builder()
                .flightNumber("ABC123")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(BigDecimal.valueOf(100000))
                .newPrice(BigDecimal.valueOf(80000))
                .build();

        // when
        String content = formatter.createContent(dto);

        // then
        assertThat(content).isEqualTo("항공편 ID: ABC123\n좌석: ECONOMY\n가격 변동: 100000 -> 80000");
    }
}
