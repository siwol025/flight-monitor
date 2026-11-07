package com.siwol025.flight_monitor.booking.service;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
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
}
