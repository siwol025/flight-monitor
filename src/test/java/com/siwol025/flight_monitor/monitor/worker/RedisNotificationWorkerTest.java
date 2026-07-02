package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisNotificationWorkerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private NotificationContentFormatter formatter;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private RedisNotificationWorker worker;

    @Test
    void processNotification_포맷터의_createSubject_와_createContent_호출됨() throws Exception {
        // given
        PriceDropNotificationDto dto = PriceDropNotificationDto.builder()
                .flightId(1L)
                .flightNumber("KE001")
                .seatGrade(SeatGrade.ECONOMY)
                .oldPrice(new BigDecimal("300000"))
                .newPrice(new BigDecimal("250000"))
                .detectedAt(LocalDateTime.now())
                .build();

        String dtoJson = "{\"flightId\":1,\"flightNumber\":\"KE001\"}";

        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.rightPop(any())).willReturn(dtoJson);
        given(objectMapper.readValue(anyString(), any(Class.class))).willReturn(dto);
        given(subscriptionService.getSubscribers(dto.flightId()))
                .willReturn(List.of(new UserEmailDto("user@test.com")));
        given(formatter.createSubject(any())).willReturn("테스트 제목");
        given(formatter.createContent(any())).willReturn("테스트 본문");

        // when
        worker.processNotification();

        // then
        then(formatter).should().createSubject(dto.flightNumber());
        then(formatter).should().createContent(dto);
    }
}
