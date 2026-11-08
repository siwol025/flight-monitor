package com.siwol025.flight_monitor.notification.producer;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!mock")
public class NotificationProducer implements NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Executor notificationEventExecutor;
    private final Duration confirmTimeout;

    public NotificationProducer(RabbitTemplate rabbitTemplate,
                                @Qualifier("notificationEventExecutor") Executor notificationEventExecutor,
                                @Value("${notification.price-drop.confirm-timeout:10s}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.notificationEventExecutor = notificationEventExecutor;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public CompletionStage<Void> publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        CorrelationData correlation = new CorrelationData();
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFICATION_EXCHANGE, RabbitMqConfig.PRICE_DROP_EVENTS_QUEUE,
                    priceDropNotificationDto, correlation);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new PriceDropPublishException("전송 실패: " + e.getMessage(), e));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        // 확인은 브로커 I/O 스레드(또는 시간 초과 타이머)에서 오므로 판정과 이어지는 작업(최신가 갱신)은 알림 executor로 옮긴다.
        // executor가 거부하면 실패로 본다(그 스레드에서 이어지는 작업을 돌리지 않는다)
        correlation.getFuture()
                .orTimeout(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((confirm, failure) -> {
                    try {
                        notificationEventExecutor.execute(() -> settle(result, correlation, confirm, failure));
                    } catch (RejectedExecutionException e) {
                        result.completeExceptionally(new PriceDropPublishException("알림 executor 포화로 확인 처리 거부", e));
                    }
                });
        return result;
    }

    private static void settle(CompletableFuture<Void> result, CorrelationData correlation,
                               CorrelationData.Confirm confirm, Throwable failure) {
        if (failure != null) {
            String reason = failure instanceof TimeoutException ? "확인 대기 시간 초과" : "확인 실패: " + failure.getMessage();
            result.completeExceptionally(new PriceDropPublishException(reason, failure));
            return;
        }
        if (!confirm.ack()) {
            result.completeExceptionally(new PriceDropPublishException("브로커 확인 거부(nack): " + confirm.reason()));
            return;
        }
        // 라우팅되지 않은 메시지도 브로커는 ack한다. return은 확인보다 먼저 CorrelationData에 채워진다
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            result.completeExceptionally(new PriceDropPublishException(
                    "라우팅 실패: " + returned.getReplyCode() + " " + returned.getReplyText()));
            return;
        }
        result.complete(null);
    }
}
