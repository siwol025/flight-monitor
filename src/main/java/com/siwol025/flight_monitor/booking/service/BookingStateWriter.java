package com.siwol025.flight_monitor.booking.service;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 예매 상태 전이. 서버가 여럿이어도 한 곳만 바꾸도록 기대 상태를 조건으로 건 UPDATE를 짧은 트랜잭션으로 바로 커밋하고,
 * 바뀌었는지(영향 행 1)를 돌려준다. false면 다른 서버가 먼저 바꾼 것이다. 외부 호출은 이 커밋 뒤에 한다.
 */
@Component
@RequiredArgsConstructor
public class BookingStateWriter {

    private final BookingRepository bookingRepository;

    public boolean transition(long bookingId, BookingStatus from, BookingStatus to) {
        return bookingRepository.transition(bookingId, from.name(), to.name()) == 1;
    }

    /** HELD → PAYMENT_CONFIRMING. 이 예매의 Toss 주문번호일 때만 바꾸고 승인할 paymentKey를 남긴다 */
    public boolean startConfirm(long bookingId, String tossOrderId, String paymentKey) {
        return bookingRepository.startConfirm(bookingId, tossOrderId, paymentKey) == 1;
    }

    /** 승인 중인 예매의 Toss 멱등키를 새 키로 바꾼다. 옛 키일 때만 바꾸므로 새 키는 한 서버만 정한다 */
    public boolean reissueTossKey(long bookingId, String oldKey, String newKey) {
        return bookingRepository.reissueTossKey(bookingId, oldKey, newKey) == 1;
    }

    /** PAYMENT_CONFIRMING → PAYMENT_APPROVED. Toss 승인 시각(UTC)을 남긴다 */
    public boolean approvePayment(long bookingId, Instant approvedAt) {
        return bookingRepository.approvePayment(bookingId,
                approvedAt == null ? null : LocalDateTime.ofInstant(approvedAt, ZoneOffset.UTC)) == 1;
    }

    /**
     * PAYMENT_CONFIRMING → HELD. 결제가 확정 실패(거절·만료)해 청구되지 않았을 때만 부른다. 새 Toss 주문번호·새 Toss 키로
     * 바꾸고 paymentKey를 비운다
     */
    public boolean reopenPayment(long bookingId, String newTossOrderId, String newTossIdempotencyKey) {
        return bookingRepository.reopenPayment(bookingId, newTossOrderId, newTossIdempotencyKey) == 1;
    }

    /**
     * HOLD_REQUESTED → HELD. hold 주문 ID·결제 기한·고객 결제 금액(원)과 이 예매의 Toss 주문번호·Toss 멱등키를 함께 고정한다
     */
    public boolean hold(long bookingId, String orderId, String bookingReference, Instant paymentRequiredBy,
                        long chargeAmount, String tossOrderId, String tossIdempotencyKey) {
        LocalDateTime requiredBy = paymentRequiredBy == null ? null
                : LocalDateTime.ofInstant(paymentRequiredBy, ZoneOffset.UTC);
        return bookingRepository.hold(bookingId, orderId, bookingReference, requiredBy, chargeAmount, tossOrderId,
                tossIdempotencyKey) == 1;
    }

    /** HOLD_REQUESTED → FAILED. hold가 만들어지지 않았다고 확인된 경우만 부른다(원인 code를 남긴다) */
    public boolean failHold(long bookingId, String failureCode) {
        return bookingRepository.failHold(bookingId, failureCode) == 1;
    }
}
