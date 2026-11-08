package com.siwol025.flight_monitor.booking.messaging;

import com.siwol025.flight_monitor.booking.saga.DuffelPaymentStep;
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
public class DuffelPaymentListener {

    private final DuffelPaymentStep duffelPaymentStep;

    // 예외는 결제가 만들어지지 않았다고 확실할 때만 나온다(DuffelPaymentStep). 재시도는 이 메서드를 다시 부르므로
    // (advice chain 안쪽은 첫 시도만 거친다) 시도별 실패 로그는 여기서 남긴다
    @RabbitListener(queues = BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE,
            containerFactory = "duffelPaymentListenerContainerFactory")
    public void onCommand(@Payload BookingCommand command,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        try {
            duffelPaymentStep.handle(command.bookingId());
        } catch (RuntimeException e) {
            log.warn("[DuffelPaymentRetry] 결제 처리 실패(결제 미생성, 재시도): {} id={} bookingId={} cause={}",
                    BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, messageId, command.bookingId(),
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            throw e;
        }
    }
}
