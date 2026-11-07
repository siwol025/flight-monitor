package com.siwol025.flight_monitor.booking.checker;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.messaging.DuffelPaymentPublisher;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.saga.DuffelPaymentStep;
import com.siwol025.flight_monitor.booking.service.CheckoutService;
import com.siwol025.flight_monitor.booking.service.PaymentConfirmService;
import java.time.Duration;
import java.util.function.LongConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결과를 모르는 채 멈춘 예매를 사용자 요청과 상관없이 확인한다. 외부 호출은 읽기 조회, 조회로 미승인을 확인한 뒤의 Toss
 * 재승인, 결제 큐 재발행뿐이다. Duffel POST(hold·결제)는 보내지 않는다.
 *
 * <p>모든 서버에서 동시에 돌아도 된다(락 없음). 상태 전이는 조건부 UPDATE라 한 서버만 바꾸고, 재발행 중복은 소비자 claim이
 * 걸러 낸다. 멈춤 판정은 DB NOW(6) 기준이다.</p>
 */
@Slf4j
@Component
@Profile("!mock")
public class BookingStatusChecker {

    private final BookingRepository bookingRepository;
    private final CheckoutService checkoutService;
    private final PaymentConfirmService paymentConfirmService;
    private final DuffelPaymentPublisher duffelPaymentPublisher;
    private final DuffelPaymentStep duffelPaymentStep;
    private final Duration staleAfter;
    private final Duration quietPeriod;

    public BookingStatusChecker(BookingRepository bookingRepository, CheckoutService checkoutService,
                                PaymentConfirmService paymentConfirmService,
                                DuffelPaymentPublisher duffelPaymentPublisher, DuffelPaymentStep duffelPaymentStep,
                                @Value("${booking.checker.stale-after:2m}") Duration staleAfter,
                                @Value("${booking.checker.quiet-period:10m}") Duration quietPeriod) {
        this.bookingRepository = bookingRepository;
        this.checkoutService = checkoutService;
        this.paymentConfirmService = paymentConfirmService;
        this.duffelPaymentPublisher = duffelPaymentPublisher;
        this.duffelPaymentStep = duffelPaymentStep;
        this.staleAfter = staleAfter;
        this.quietPeriod = quietPeriod;
    }

    @Scheduled(fixedDelayString = "${booking.checker.fixed-delay:60s}")
    public void check() {
        each(BookingStatus.HOLD_REQUESTED, staleAfter, checkoutService::resolvePendingHold);
        each(BookingStatus.PAYMENT_CONFIRMING, staleAfter, paymentConfirmService::resolveUnknown);
        // 결제를 아직 보내지 않은 상태라 다시 보내도 안전하다(소비자 claim이 중복을 거른다)
        each(BookingStatus.PAYMENT_APPROVED, staleAfter, duffelPaymentPublisher::publish);
        each(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, quietPeriod, duffelPaymentStep::checkUnconfirmed);
    }

    // 한 예매의 실패가 다른 예매 확인을 막지 않는다. 실패한 예매는 다음 주기에 다시 본다
    private void each(BookingStatus status, Duration olderThan, LongConsumer action) {
        for (long bookingId : bookingRepository.findIdsInStatusSince(status.name(), olderThan.toSeconds())) {
            try {
                action.accept(bookingId);
            } catch (RuntimeException e) {
                log.warn("[BookingStatusChecker] 확인 실패(다음 주기에 다시): bookingId={}, status={}", bookingId, status,
                        e);
            }
        }
    }
}
