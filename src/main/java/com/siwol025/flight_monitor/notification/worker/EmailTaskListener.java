package com.siwol025.flight_monitor.notification.worker;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.mail.NotificationService;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class EmailTaskListener {

    private final NotificationService notificationService;

    // 발송 예외는 삼키지 않는다. emailTaskListenerContainerFactory의 재시도가 재시도·DLQ로 처리한다.
    @RabbitListener(queues = RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, containerFactory = "emailTaskListenerContainerFactory")
    public void processEmailTask(@Payload EmailSendTaskDto taskDto,
                                 @Header(AmqpHeaders.MESSAGE_ID) String messageId,
                                 @Header(AmqpHeaders.TIMESTAMP) Date publishedAt) {
        notificationService.sendPriceDropNotification(taskDto);
        // latency = fan-out 발행(메시지 timestamp) → 발송 완료. 재시도 대기도 포함된다.
        log.info("[EmailTaskListener] 알림 전송 완료: {} id={} latency={}ms", RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE,
                messageId, System.currentTimeMillis() - publishedAt.getTime());
    }
}
