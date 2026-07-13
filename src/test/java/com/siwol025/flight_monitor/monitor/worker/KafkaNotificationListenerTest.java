package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.service.AlertConditionChecker;
import com.siwol025.flight_monitor.monitor.service.NotificationCooldownManager;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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

    @Mock
    private NotificationCooldownManager cooldownManager;

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
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("user1@test.com", null, null),
                new SubscriberWithConditionDto("user2@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire(any(), any(), any())).willReturn(true);

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

        given(subscriptionService.getSubscribersWithCondition(flightId))
                .willReturn(List.of(new SubscriberWithConditionDto("subscriber@test.com", null, null)));
        given(formatter.createSubject(any())).willReturn("테스트 제목");
        given(formatter.createContent(any())).willReturn("테스트 본문");
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire(any(), any(), any())).willReturn(true);

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

        given(subscriptionService.getSubscribersWithCondition(flightId))
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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), isNull())).willReturn(true);
        given(cooldownManager.tryAcquire(any(), any(), any())).willReturn(true);

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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), eq(targetPrice), isNull())).willReturn(true);
        given(cooldownManager.tryAcquire(any(), any(), any())).willReturn(true);

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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), eq(threshold))).willReturn(true);
        given(cooldownManager.tryAcquire(any(), any(), any())).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_쿨다운_아님_이메일발송_큐_전송됨() {
        // given
        Long flightId = 10L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE010")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("cooldown@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire("cooldown@test.com", flightId, SeatGrade.ECONOMY)).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(cooldownManager).should().tryAcquire("cooldown@test.com", flightId, SeatGrade.ECONOMY);
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_쿨다운_중_이메일발송_큐_생략됨() {
        // given
        Long flightId = 11L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE011")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("incooldown@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire("incooldown@test.com", flightId, SeatGrade.ECONOMY)).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then — 획득 실패(쿨다운 중)이므로 발송 없음
        then(kafkaTemplate).should(never())
                .send(anyString(), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_알림조건_불만족_쿨다운_체크_안함() {
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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then — shouldNotify=false이므로 쿨다운 획득(Redis 접근)은 절대 발생하지 않아야 함
        then(cooldownManager).shouldHaveNoInteractions();
    }

    @Test
    void processNotification_쿨다운_획득시_발송됨() {
        // given
        Long flightId = 13L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE013")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("record@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire("record@test.com", flightId, SeatGrade.ECONOMY)).willReturn(true);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then — 원자적 획득 성공 시 발송, 발송 성공 시 release 없음
        then(cooldownManager).should().tryAcquire("record@test.com", flightId, SeatGrade.ECONOMY);
        then(cooldownManager).should(never()).release(any(), any(), any());
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_다구독자_일부만_쿨다운_해당자만_발송됨() {
        // given
        Long flightId = 14L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE014")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("first@test.com", null, null),
                new SubscriberWithConditionDto("second@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire("first@test.com", flightId, SeatGrade.ECONOMY)).willReturn(true);
        given(cooldownManager.tryAcquire("second@test.com", flightId, SeatGrade.ECONOMY)).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then — 획득 성공한 first 만 발송
        then(kafkaTemplate).should(times(1))
                .send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class));
    }

    @Test
    void processNotification_발송실패시_release_호출되어_쿨다운_해제됨() {
        // given
        Long flightId = 15L;
        PriceDropNotificationDto eventDto = PriceDropNotificationDto.builder()
                .flightId(flightId)
                .flightNumber("KE015")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(OLD_PRICE)
                .newPrice(NEW_PRICE)
                .detectedAt(LocalDateTime.now())
                .build();

        List<SubscriberWithConditionDto> subscribers = List.of(
                new SubscriberWithConditionDto("fail@test.com", null, null)
        );

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(any(), any(), any(), any())).willReturn(true);
        given(cooldownManager.tryAcquire("fail@test.com", flightId, SeatGrade.ECONOMY)).willReturn(true);
        given(kafkaTemplate.send(eq("email-send-tasks"), anyString(), any(EmailSendTaskDto.class)))
                .willThrow(new RuntimeException("kafka unavailable"));

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then — 발송 실패 시 획득한 쿨다운을 보상 해제
        then(cooldownManager).should().release("fail@test.com", flightId, SeatGrade.ECONOMY);
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

        given(subscriptionService.getSubscribersWithCondition(flightId)).willReturn(subscribers);
        given(alertConditionChecker.shouldNotify(eq(OLD_PRICE), eq(NEW_PRICE), isNull(), eq(threshold))).willReturn(false);

        // when
        kafkaNotificationListener.processNotification(eventDto);

        // then
        then(kafkaTemplate).shouldHaveNoInteractions();
    }
}
