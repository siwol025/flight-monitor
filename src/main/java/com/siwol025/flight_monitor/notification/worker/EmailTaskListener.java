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
import org.springframework.core.NestedExceptionUtils;
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
    // 재시도는 이 메서드를 다시 부르므로(advice chain 안쪽은 첫 시도만 거친다) 시도별 실패 로그는 여기서 남긴다.
    @RabbitListener(queues = RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, containerFactory = "emailTaskListenerContainerFactory")
    public void processEmailTask(@Payload EmailSendTaskDto taskDto,
                                 @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId,
                                 @Header(name = AmqpHeaders.TIMESTAMP, required = false) Date publishedAt) {
        try {
            notificationService.sendPriceDropNotification(taskDto);
        } catch (RuntimeException e) {
            log.warn("[EmailTaskRetry] 발송 실패: {} id={} cause={}", RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE, messageId,
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            throw e;
        }
        // latency = fan-out 발행(메시지 timestamp) → 발송 완료. 재시도 대기도 포함된다.
        // id·timestamp는 로그에만 쓰므로 없어도 발송은 정상 처리한다
        String latency = publishedAt == null ? "unknown" : (System.currentTimeMillis() - publishedAt.getTime()) + "ms";
        log.info("[EmailTaskListener] 알림 전송 완료: {} id={} latency={}", RabbitMqConfig.EMAIL_SEND_TASKS_QUEUE,
                messageId, latency);
    }
}
