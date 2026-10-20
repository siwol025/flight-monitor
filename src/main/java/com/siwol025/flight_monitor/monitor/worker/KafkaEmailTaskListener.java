package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka")
@Profile("!mock")
public class KafkaEmailTaskListener {

    private final NotificationService notificationService;

    // 발송 예외는 삼키지 않는다. emailTaskListenerContainerFactory의 에러 핸들러가 재시도·DLT로 처리한다.
    @KafkaListener(topics = "email-send-tasks", groupId = "email-sender-group", concurrency = "50",
            containerFactory = "emailTaskListenerContainerFactory")
    public void processEmailTask(@Payload EmailSendTaskDto taskDto,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long publishedAt) {
        notificationService.sendPriceDropNotification(taskDto);
        // latency = fan-out 발행(레코드 타임스탬프) → 발송 완료. 재시도 대기도 포함된다.
        log.info("[KafkaEmailTaskListener] 알림 전송 완료: {}-{}@{} latency={}ms",
                topic, partition, offset, System.currentTimeMillis() - publishedAt);
    }
}
