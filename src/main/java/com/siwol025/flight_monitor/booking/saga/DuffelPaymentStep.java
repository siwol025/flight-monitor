package com.siwol025.flight_monitor.booking.saga;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.fare.FlightOrder;
import com.siwol025.flight_monitor.fare.FlightOrderException;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.OrderFailureKind;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Duffel hold 결제와 발권 확인(⑥). Duffel 결제는 멱등키가 없으므로 결제(POST)를 보내기 전에 DUFFEL_PAYING을 기록해 "한 번
 * 보냈다"를 남기고, 결과를 모르면 다시 보내지 않고 DUFFEL_PAYMENT_UNCONFIRMED로 보류한다(이후 확인은 읽기 조회뿐).
 *
 * <p>{@link #handle}이 던지는 예외는 리스너 재시도로 이 메서드를 다시 부른다. 그래서 결제가 만들어지지 않았다고 확실할 때
 * (결제 전 조회 실패, 429·503·전송 전 실패)만 상태를 PAYMENT_APPROVED로 되돌린 뒤 던지고, 결제를 보낸 뒤에는 던지지 않는다.
 * 재시도·재전달이 DUFFEL_PAYING을 보면 결제 도중 멈춘 것일 수 있으므로 보류로 보낸다.</p>
 *
 * <p>외부 호출은 DB 트랜잭션 밖에서 하고, 상태 변경은 조건부 UPDATE로만 한다(서버가 여럿이어도 한 곳만 바꾼다).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuffelPaymentStep {

    private final FlightOrderGateway flightOrderGateway;
    private final BookingStateWriter bookingStateWriter;
    private final BookingRepository bookingRepository;

    /** 리스너가 부른다. 결제가 만들어지지 않았다고 확실한 일시 실패만 예외로 알린다(재시도해도 중복 결제가 없다) */
    public void handle(long bookingId) {
        Optional<Booking> found = bookingRepository.findById(bookingId);
        if (found.isEmpty()) {
            log.error("[DuffelPaymentStep] 없는 예매(ack): bookingId={}", bookingId);
            return;
        }
        Booking booking = found.get();
        if (booking.getStatus() == BookingStatus.DUFFEL_PAYING) {
            onPayingAgain(booking);
            return;
        }
        // 0행이면 다른 소비자가 가져갔거나 이미 지난 단계다
        if (!bookingStateWriter.transition(bookingId, BookingStatus.PAYMENT_APPROVED, BookingStatus.DUFFEL_PAYING)) {
            log.info("[DuffelPaymentStep] 결제할 상태가 아님(ack): bookingId={}, status={}", bookingId,
                    booking.getStatus());
            return;
        }
        FlightOrder order = orderBeforePayment(booking);
        if (order.paidAt() == null && !pay(booking, order)) {
            return;
        }
        confirmTicketed(bookingId, booking.getProviderOrderId());
    }

    /**
     * 확인 스케줄러가 부른다. 보류된 예매를 읽기 조회로만 확인한다: 결제·발권이 보이면 TICKETED, 아니면 보류 유지.
     * 미결제로 확인돼도 다시 결제하지 않는다(이번 범위의 결정).
     */
    public void checkUnconfirmed(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        if (booking.getStatus() != BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED) {
            return;
        }
        Optional<FlightOrder> order;
        try {
            order = flightOrderGateway.findOrder(booking.getProviderOrderId());
        } catch (RuntimeException e) {
            log.warn("[DuffelPaymentStep] 보류 예매 조회 실패(보류 유지): bookingId={}", bookingId, e);
            return;
        }
        if (order.isEmpty()) {
            log.warn("[DuffelPaymentStep] 보류 예매의 주문이 없음(보류 유지): bookingId={}, orderId={}", bookingId,
                    booking.getProviderOrderId());
            return;
        }
        FlightOrder current = order.get();
        if (current.paidAt() == null) {
            log.warn("[DuffelPaymentStep] 보류 예매가 미결제로 확인됨(재결제하지 않고 보류 유지, 운영자 확인 필요): "
                    + "bookingId={}, orderId={}", bookingId, current.orderId());
            return;
        }
        if (current.ticketNumbers().isEmpty()) {
            log.info("[DuffelPaymentStep] 결제됐으나 아직 미발권(보류 유지): bookingId={}", bookingId);
            return;
        }
        bookingStateWriter.ticket(bookingId, current.paidAt(), current.ticketNumbers());
    }

    // 이전 시도가 결제를 보냈는지 모른다(재시도·재전달·결제 도중 종료). 다시 보내지 않고 보류한다
    private void onPayingAgain(Booking booking) {
        if (booking.getFailureCode() != null) {
            // 결제가 확정 거절돼 원인을 남긴 예매(보상은 범위 밖)
            log.info("[DuffelPaymentStep] 결제 거절로 멈춘 예매(ack): bookingId={}, failureCode={}", booking.getId(),
                    booking.getFailureCode());
            return;
        }
        if (bookingStateWriter.transition(booking.getId(), BookingStatus.DUFFEL_PAYING,
                BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED)) {
            log.warn("[DuffelPaymentStep] DUFFEL_PAYING에서 다시 시작됨(결제를 다시 보내지 않고 보류): bookingId={}",
                    booking.getId());
        }
    }

    // 결제 전 조회. 아직 결제를 보내지 않았으므로 실패하면 PAYMENT_APPROVED로 되돌리고 던진다(재시도 안전)
    private FlightOrder orderBeforePayment(Booking booking) {
        try {
            return flightOrderGateway.findOrder(booking.getProviderOrderId())
                    .orElseThrow(() -> new IllegalStateException("hold 주문이 없습니다: " + booking.getProviderOrderId()));
        } catch (RuntimeException e) {
            release(booking.getId());
            throw e;
        }
    }

    // 결제를 보낸다. 보낸 뒤 결과를 모르면 예외 없이 보류한다. 다음 단계(발권 확인)로 갈지 돌려준다
    private boolean pay(Booking booking, FlightOrder order) {
        long bookingId = booking.getId();
        try {
            flightOrderGateway.payHeldOrder(order.orderId(), order.amount(), order.currency(),
                    String.valueOf(bookingId));
            return true;
        } catch (FlightOrderException e) {
            if (notCreated(e.getKind())) {
                // 결제가 만들어지지 않았다고 확실하다: 되돌리고 던져 리스너가 다시 시도하게 한다
                release(bookingId);
                throw e;
            }
            if (e.getKind() == OrderFailureKind.UNKNOWN) {
                unconfirmed(bookingId, e);
                return false;
            }
            log.error("[DuffelPaymentStep] Duffel 결제가 거절됨(보상은 범위 밖, DUFFEL_PAYING 유지): bookingId={}, "
                    + "kind={}, code={}", bookingId, e.getKind(), e.getCode(), e);
            bookingStateWriter.recordPaymentFailure(bookingId,
                    e.getCode() != null ? e.getCode() : e.getKind().name());
            return false;
        } catch (RuntimeException e) {
            // 201 본문 해석 실패 등: 결제됐을 수 있다
            unconfirmed(bookingId, e);
            return false;
        }
    }

    // 결제 뒤 조회로 발권을 확인한다. 결제를 이미 보냈으므로 무엇이 실패해도 던지지 않고 보류한다(확인은 스케줄러)
    private void confirmTicketed(long bookingId, String orderId) {
        try {
            Optional<FlightOrder> order = flightOrderGateway.findOrder(orderId);
            if (order.isPresent() && order.get().paidAt() != null && !order.get().ticketNumbers().isEmpty()) {
                bookingStateWriter.ticket(bookingId, order.get().paidAt(), order.get().ticketNumbers());
                return;
            }
            log.warn("[DuffelPaymentStep] 결제 뒤 조회에 발권이 없음(보류, 스케줄러가 다시 확인): bookingId={}", bookingId);
        } catch (RuntimeException e) {
            log.warn("[DuffelPaymentStep] 결제 뒤 조회 실패(보류, 스케줄러가 다시 확인): bookingId={}", bookingId, e);
        }
        bookingStateWriter.transition(bookingId, BookingStatus.DUFFEL_PAYING, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
    }

    private void unconfirmed(long bookingId, RuntimeException e) {
        log.warn("[DuffelPaymentStep] Duffel 결제 결과 불명확(재요청하지 않고 보류): bookingId={}", bookingId, e);
        bookingStateWriter.transition(bookingId, BookingStatus.DUFFEL_PAYING, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
    }

    private void release(long bookingId) {
        bookingStateWriter.transition(bookingId, BookingStatus.DUFFEL_PAYING, BookingStatus.PAYMENT_APPROVED);
    }

    private static boolean notCreated(OrderFailureKind kind) {
        return kind == OrderFailureKind.NOT_SENT || kind == OrderFailureKind.RATE_LIMITED
                || kind == OrderFailureKind.NOT_CREATED;
    }
}
