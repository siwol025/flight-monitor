package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.service.AlertConditionChecker;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationListenerTest {

    private static final BigDecimal OLD_PRICE = new BigDecimal("300000");
    private static final BigDecimal NEW_PRICE = new BigDecimal("250000");


    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private NotificationContentFormatter formatter;

    @Mock
    private AlertConditionChecker alertConditionChecker;

    @InjectMocks
    private KafkaNotificationListener kafkaNotificationListener;

    @BeforeEach
    void stubSendSucceeds() {
        // 실제 계약: send()는 항상 future를 반환한다. 기본값을 성공 완료로 두고, 실패 시나리오에서만 재정의한다.
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void processNotification_구독자_존재시_각_구독자에게_이메일발송_태스크_Kafka_전송됨() {
        // given
        Long flightId = 1L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE001")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("user1@test.com", null, null),
                new SubscriberWithConditionDto("user2@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(2))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
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

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY))
                .willReturn(List.of(new SubscriberWithConditionDto("subscriber@test.com", null, null)));
        given(formatter.createSubject(any())).willReturn("테스트 제목");
        given(formatter.createContent(any())).willReturn("테스트 본문");
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(dto);

        // then
        then(formatter).should().createSubject(dto.flightNumber());
        then(formatter).should().createContent(dto);
    }

    @Test
    void processNotification_구독자_없음_발송없음() {
        // given
        Long flightId = 4L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("LJ404")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("200000"))
                .newPrice(new BigDecimal("180000"))
                .detectedAt(LocalDateTime.now())
                .build();

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY))
                .willReturn(List.of());

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_조건없는구독자_하락시_이메일_발송됨() {
        // given
        Long flightId = 5L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE005")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("a@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), isNull())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_targetPrice_달성구독자_이메일_발송됨() {
        // given
        Long flightId = 6L;
        BigDecimal targetPrice = new BigDecimal("260000");
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE006")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("b@test.com", targetPrice, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), eq(targetPrice), isNull())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_targetPrice_미달구독자_이메일_미발송됨() {
        // given
        Long flightId = 7L;
        BigDecimal targetPrice = new BigDecimal("200000");
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE007")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("c@test.com", targetPrice, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), eq(targetPrice), isNull())).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_threshold_달성구독자_이메일_발송됨() {
        // given
        Long flightId = 8L;
        BigDecimal threshold = new BigDecimal("10.00");
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE008")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("d@test.com", null, threshold)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), eq(threshold))).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_알림조건_불만족_발송안됨() {
        // given
        Long flightId = 12L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE012")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("nocondition@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_한구독자_동기발송실패해도_나머지구독자_발송됨() {
        // given
        Long flightId = 15L;
        PriceDropNotificationDto eventDto = priceDropEvent(flightId, "KE015");
        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(List.of(
                new SubscriberWithConditionDto("fail@test.com", null, null),
                new SubscriberWithConditionDto("ok@test.com", null, null)));
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(kafkaTemplate.send(eq("email-send-tasks"), eq("KE015fail@test.com"), any(EmailSendTaskDto.class)))
                .willThrow(new RuntimeException("kafka unavailable"));

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should()
                .send(eq("email-send-tasks"), eq("KE015ok@test.com"), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_threshold_미달구독자_이메일_미발송됨() {
        // given
        Long flightId = 9L;
        BigDecimal threshold = new BigDecimal("20.00");
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE009")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("e@test.com", null, threshold)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), eq(threshold))).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_동일구독자_반복하락이벤트마다_발송됨() {
        // given: 시간 기반 쿨다운 없이, 조건을 만족하는 하락 이벤트는 매번 알린다
        Long flightId = 30L;
        PriceDropNotificationDto eventDto = priceDropEvent(flightId, "KE030");
        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.ECONOMY))
                .willReturn(List.of(new SubscriberWithConditionDto("repeat@test.com", null, null)));
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(2))
                .send(eq("email-send-tasks"), eq("KE030repeat@test.com"), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_이벤트좌석등급으로_구독자조회됨() {
        // given: 비즈니스 좌석 가격 하락 이벤트
        Long flightId = 40L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE040")
                .seatGrade(SeatGrade.BUSINESS)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();
        given(subscriptionService.getSubscribersWithCondition(flightId, SeatGrade.BUSINESS))
                .willReturn(List.of(new SubscriberWithConditionDto("business@test.com", null, null)));
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then: 비즈니스 구독자에게만 발송된다
        then(kafkaTemplate).should()
                .send(eq("email-send-tasks"), eq("KE040business@test.com"), any(EmailSendTaskDto.class));
    }

    private PriceDropNotificationDto priceDropEvent(Long flightId, String flightNumber) {
        return PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber(flightNumber)
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();
    }
}
