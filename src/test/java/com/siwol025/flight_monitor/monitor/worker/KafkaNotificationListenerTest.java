package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationListenerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private NotificationContentFormatter formatter;

    @InjectMocks
    private KafkaNotificationListener kafkaNotificationListener;

    @Test
    void processNotification_구독자_존재시_각_구독자에게_이메일발송_태스크_Kafka_전송됨() {
        // given
        Long flightId = 1L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE001")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("300000"))
                .newPrice(new BigDecimal("250000"))
                .detectedAt(LocalDateTime.now())
                .build();

        List<UserEmailDto> subscribers = List.of(
                new UserEmailDto("user1@test.com"),
                new UserEmailDto("user2@test.com")
        );

        given(subscriptionService.getSubscribers(flightId)).willReturn(subscribers);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(2))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_구독자_없으면_Kafka_전송_안됨() {
        // given
        Long flightId = 2L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("OZ202")
                .seatGrade(SeatGrade.BUSINESS)
                .oldPrice(new BigDecimal("500000"))
                .newPrice(new BigDecimal("450000"))
                .detectedAt(LocalDateTime.now())
                .build();

        given(subscriptionService.getSubscribers(flightId)).willReturn(List.of());

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_포맷터의_createSubject_와_createContent_호출됨() {
        // given
        Long flightId = 3L;
        PriceDropNotificationDto dto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE777")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("400000"))
                .newPrice(new BigDecimal("350000"))
                .detectedAt(LocalDateTime.now())
                .build();

        given(subscriptionService.getSubscribers(flightId))
                .willReturn(List.of(new UserEmailDto("subscriber@test.com")));
        given(formatter.createSubject(any())).willReturn("테스트 제목");
        given(formatter.createContent(any())).willReturn("테스트 본문");

        // when
        kafkaNotificationListener.processNotification(dto);

        // then
        then(formatter).should().createSubject(dto.flightNumber());
        then(formatter).should().createContent(dto);
    }
}
