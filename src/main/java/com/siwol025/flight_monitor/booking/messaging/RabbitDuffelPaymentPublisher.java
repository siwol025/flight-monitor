package com.siwol025.flight_monitor.booking.messaging;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

/**
 * 결제 큐 발행기. 승인 API는 프로필 조건 없이 모든 인스턴스에서 돌므로 이 빈도 프로필 조건을 두지 않는다
 * (빠지면 승인한 예매가 발행 없이 스케줄러 재발행만 기다린다). 실패는 호출한 쪽으로 던진다.
 * 브로커는 라우팅되지 않은 메시지도 ack하므로 publisher confirm과 mandatory return을 함께 기다려 큐에 들어갔는지 판정한다.
 */
@Slf4j
@Component
public class RabbitDuffelPaymentPublisher implements DuffelPaymentPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitDuffelPaymentPublisher(RabbitTemplate rabbitTemplate,
                                        @Value("${booking.payment-publish.confirm-timeout:3s}") String confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        // 변환 서비스가 없는 컨텍스트(ApplicationContextRunner)에서도 "3s" 형식을 읽도록 직접 해석한다
        this.confirmTimeout = DurationStyle.detectAndParse(confirmTimeout);
    }

    @Override
    public void publish(long bookingId) {
        try {
            awaitConfirm(send(bookingId));
        } catch (DuffelPaymentPublishException e) {
            log.warn("[RabbitDuffelPaymentPublisher] 결제 명령 발행 실패: bookingId={}, routingKey={}, cause={}",
                    bookingId, BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, e.getMessage());
            throw e;
        }
    }

    private CorrelationData send(long bookingId) {
        CorrelationData correlation = new CorrelationData();
        try {
            rabbitTemplate.convertAndSend(BookingMessagingConfig.BOOKING_EXCHANGE,
                    BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, new BookingCommand(bookingId), correlation);
        } catch (RuntimeException e) {
            throw new DuffelPaymentPublishException("전송 실패: " + e.getMessage(), e);
        }
        return correlation;
    }

    private void awaitConfirm(CorrelationData correlation) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new DuffelPaymentPublishException("확인 대기 시간 초과", e);
        } catch (ExecutionException e) {
            throw new DuffelPaymentPublishException("확인 실패: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DuffelPaymentPublishException("확인 대기 중 인터럽트", e);
        }
        if (!confirm.ack()) {
            throw new DuffelPaymentPublishException("브로커 확인 거부(nack): " + confirm.reason());
        }
        // 라우팅되지 않은 메시지도 브로커는 ack한다. return은 확인보다 먼저 CorrelationData에 채워진다
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new DuffelPaymentPublishException(
                    "라우팅 실패: " + returned.getReplyCode() + " " + returned.getReplyText());
        }
    }
}
