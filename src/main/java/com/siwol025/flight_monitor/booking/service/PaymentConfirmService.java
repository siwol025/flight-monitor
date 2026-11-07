package com.siwol025.flight_monitor.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.request.PaymentConfirmRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyScope;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyService;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult;
import com.siwol025.flight_monitor.booking.messaging.DuffelPaymentPublisher;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.payment.PaymentFailureKind;
import com.siwol025.flight_monitor.payment.PaymentGateway;
import com.siwol025.flight_monitor.payment.PaymentGatewayException;
import com.siwol025.flight_monitor.payment.PaymentSnapshot;
import com.siwol025.flight_monitor.payment.PaymentStatus;
import com.siwol025.flight_monitor.user.domain.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 결제 승인(⑤): Idempotency-Key 선점 → 금액 검증 → HELD → PAYMENT_CONFIRMING(조건부 UPDATE) → Toss 승인(④에서 저장한
 * Toss 멱등키) → PAYMENT_APPROVED → Duffel 결제 요청 발행.
 *
 * <p>승인 결과를 모르면(타임아웃·5xx·409·ALREADY_*·응답 해석 실패) 먼저 조회로 청구 여부를 확인하고, 미승인(IN_PROGRESS)을
 * 확인한 뒤에만 같은 Toss 키로 다시 보낸다. 같은 키가 5xx만 재생하면 새 Toss 키를 조건부 UPDATE로 정한 뒤 그 키로 보낸다
 * (같은 결제의 두 번째 승인은 Toss가 ALREADY_PROCESSED_PAYMENT로 막는다). 끝내 모르면 PAYMENT_CONFIRMING으로 보류하고
 * 202를 저장한다(이후 확인은 스케줄러). 503(키 행 삭제, 같은 키 재처리)은 청구되지 않았다고 확인된 경우에만 쓴다.</p>
 *
 * <p>외부 호출은 DB 트랜잭션 밖에서 하고, 상태 변경은 조건부 UPDATE로만 한다(서버가 여럿이어도 한 곳만 바꾼다).</p>
 */
@Slf4j
@Service
@EnableConfigurationProperties(PaymentConfirmProperties.class)
public class PaymentConfirmService {

    // 인라인 재요청 간격: 0.5s, 1s
    private static final long BACKOFF_MILLIS = 500;

    private final PaymentGateway paymentGateway;
    private final IdempotencyService idempotencyService;
    private final BookingStateWriter bookingStateWriter;
    private final BookingRepository bookingRepository;
    private final ObjectProvider<DuffelPaymentPublisher> publisher;
    private final PaymentConfirmProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentConfirmService(PaymentGateway paymentGateway, IdempotencyService idempotencyService,
                                 BookingStateWriter bookingStateWriter, BookingRepository bookingRepository,
                                 ObjectProvider<DuffelPaymentPublisher> publisher, PaymentConfirmProperties properties,
                                 ObjectMapper objectMapper) {
        this.paymentGateway = paymentGateway;
        this.idempotencyService = idempotencyService;
        this.bookingStateWriter = bookingStateWriter;
        this.bookingRepository = bookingRepository;
        this.publisher = publisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 이 메서드는 트랜잭션 밖에서 부른다(키 선점·결과 저장·상태 변경이 각자 바로 커밋돼야 다른 서버가 본다) */
    public IdempotentResult<Object> confirm(User user, String idempotencyKey, PaymentConfirmRequest request) {
        return idempotencyService.execute(user.getId(), IdempotencyScope.PAYMENT_CONFIRM, idempotencyKey,
                hash(request), claim -> process(user, request));
    }

    private ResponseEntity<Object> process(User user, PaymentConfirmRequest request) {
        Optional<Booking> found = bookingRepository.findByTossOrderId(request.orderId())
                .filter(booking -> booking.getUser().getId().equals(user.getId()));
        if (found.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, ErrorTag.BOOKING_NOT_FOUND);
        }
        long bookingId = found.get().getId();
        // 승인에는 successUrl 값이 아니라 저장한 금액을 보낸다. 다르면 Toss도 FORBIDDEN_REQUEST로 거절한다(실측 B)
        if (!request.amount().equals(found.get().getChargeAmount())) {
            return error(HttpStatus.BAD_REQUEST, ErrorTag.PAYMENT_AMOUNT_MISMATCH);
        }
        // 0행이면 다른 요청(다른 키·다른 서버)이 이미 승인 중이거나 끝냈다: Toss를 부르지 않고 현재 상태만 응답한다
        if (!bookingStateWriter.startConfirm(bookingId, request.orderId(), request.paymentKey())) {
            return currentState(bookingId);
        }
        return confirmUntilKnown(bookingRepository.findById(bookingId).orElseThrow());
    }

    // 재요청은 반드시 조회로 미승인을 확인한 뒤에만 한다
    private ResponseEntity<Object> confirmUntilKnown(Booking booking) {
        String tossKey = booking.getTossIdempotencyKey();
        Attempt attempt = attempt(booking, tossKey);
        boolean resent = false;
        int retries = 0;
        while (true) {
            if (attempt.approved()) {
                return approve(booking.getId(), attempt.snapshot());
            }
            if (attempt.kind() == PaymentFailureKind.REJECTED) {
                return reopen(booking.getId(), HttpStatus.BAD_REQUEST, ErrorTag.PAYMENT_REJECTED,
                        attempt.providerMessage());
            }
            if (attempt.kind() == PaymentFailureKind.EXPIRED) {
                return reopen(booking.getId(), HttpStatus.CONFLICT, ErrorTag.PAYMENT_EXPIRED, null);
            }
            Optional<PaymentSnapshot> checked = find(booking);
            if (checked.isEmpty()) {
                return currentState(booking.getId());
            }
            PaymentSnapshot snapshot = checked.get();
            switch (snapshot.status()) {
                case DONE -> {
                    if (snapshot.orderId().equals(booking.getTossOrderId())
                            && snapshot.totalAmount() == booking.getChargeAmount()) {
                        return approve(booking.getId(), snapshot);
                    }
                    log.error("[PaymentConfirmService] 승인된 결제가 예매와 다름(보류): bookingId={}, orderId={}, "
                            + "totalAmount={}", booking.getId(), snapshot.orderId(), snapshot.totalAmount());
                    return currentState(booking.getId());
                }
                case ABORTED -> {
                    return reopen(booking.getId(), HttpStatus.BAD_REQUEST, ErrorTag.PAYMENT_REJECTED, null);
                }
                case EXPIRED -> {
                    return reopen(booking.getId(), HttpStatus.CONFLICT, ErrorTag.PAYMENT_EXPIRED, null);
                }
                case IN_PROGRESS -> {
                    if (attempt.kind() == PaymentFailureKind.CONFIGURATION) {
                        return configurationFailed(booking.getId(), attempt);
                    }
                    if (retries >= properties.getConfirmInlineRetries() || !backoff(retries)) {
                        return currentState(booking.getId());
                    }
                    retries++;
                    if (resent && attempt.serverError()) {
                        // D4: 같은 키가 첫 5xx만 재생한다. 미승인을 확인했으니 새 키로 보낸다(새 키는 한 서버만 정한다)
                        String newKey = UUID.randomUUID().toString();
                        if (!bookingStateWriter.reissueTossKey(booking.getId(), tossKey, newKey)) {
                            return currentState(booking.getId());
                        }
                        log.warn("[PaymentConfirmService] 같은 Toss 키가 5xx를 재생해 새 키로 재승인: bookingId={}",
                                booking.getId());
                        tossKey = newKey;
                        resent = false;
                    } else {
                        resent = true;
                    }
                    attempt = attempt(booking, tossKey);
                }
                default -> {
                    return currentState(booking.getId());
                }
            }
        }
    }

    private Attempt attempt(Booking booking, String tossKey) {
        try {
            return Attempt.of(paymentGateway.confirm(booking.getPaymentKey(), booking.getTossOrderId(),
                    booking.getChargeAmount(), tossKey));
        } catch (PaymentGatewayException e) {
            return new Attempt(null, e.getKind(), e.getHttpStatus(), e.getProviderMessage());
        } catch (RuntimeException e) {
            // 200 본문 해석 실패 등: 승인됐을 수 있으므로 결과를 모르는 것과 같게 조회로 확인한다
            log.warn("[PaymentConfirmService] Toss 승인 응답을 해석하지 못함(조회로 확인): bookingId={}",
                    booking.getId(), e);
            return new Attempt(null, PaymentFailureKind.UNKNOWN, null, null);
        }
    }

    // 읽기 조회. 실패하면 모르는 채로 재요청하지 않는다(보류)
    private Optional<PaymentSnapshot> find(Booking booking) {
        try {
            return Optional.of(paymentGateway.find(booking.getPaymentKey()));
        } catch (RuntimeException e) {
            log.warn("[PaymentConfirmService] Toss 결제 조회 실패(보류, 스케줄러가 다시 확인): bookingId={}",
                    booking.getId(), e);
            return Optional.empty();
        }
    }

    private static boolean backoff(int retries) {
        try {
            Thread.sleep(BACKOFF_MILLIS << retries);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // 승인 커밋 뒤 발행한다. 발행이 실패해도 승인은 끝났다(멈춘 PAYMENT_APPROVED는 스케줄러가 다시 발행한다)
    private ResponseEntity<Object> approve(long bookingId, PaymentSnapshot snapshot) {
        if (!bookingStateWriter.approvePayment(bookingId, snapshot.approvedAt())) {
            return currentState(bookingId);
        }
        try {
            publisher.ifAvailable(p -> p.publish(bookingId));
        } catch (RuntimeException e) {
            log.warn("[PaymentConfirmService] Duffel 결제 요청 발행 실패(스케줄러가 다시 발행): bookingId={}", bookingId, e);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BookingStatusResponse(bookingId, BookingStatus.PAYMENT_APPROVED));
    }

    // 청구되지 않은 확정 실패: 새 Toss 주문번호·새 Toss 키로 다시 결제할 수 있게 HELD로 돌린다
    private ResponseEntity<Object> reopen(long bookingId, HttpStatus status, ErrorTag tag, String providerMessage) {
        if (!bookingStateWriter.reopenPayment(bookingId, "fm-" + UUID.randomUUID(), UUID.randomUUID().toString())) {
            return currentState(bookingId);
        }
        String message = providerMessage != null ? providerMessage : tag.getMessage();
        return ResponseEntity.status(status).body(new ErrorResponse(tag.name(), message));
    }

    // 우리 설정 문제이고 조회로 미승인을 확인했다: 같은 주문번호로 다시 승인할 수 있게 HELD로 돌리고 503(키 삭제)
    private ResponseEntity<Object> configurationFailed(long bookingId, Attempt attempt) {
        log.error("[PaymentConfirmService] Toss 승인이 설정 문제로 거부됨(미승인 확인): bookingId={}, status={}",
                bookingId, attempt.httpStatus());
        if (!bookingStateWriter.transition(bookingId, BookingStatus.PAYMENT_CONFIRMING, BookingStatus.HELD)) {
            return currentState(bookingId);
        }
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorTag.PAYMENT_PROVIDER_UNAVAILABLE);
    }

    // 승인 중·승인 뒤 상태면 202(진행은 예매 조회로 본다), 결제할 수 없는 상태면 409
    private ResponseEntity<Object> currentState(long bookingId) {
        BookingStatus status = bookingRepository.findById(bookingId).orElseThrow().getStatus();
        if (status == BookingStatus.HELD || status == BookingStatus.HOLD_REQUESTED || status == BookingStatus.FAILED) {
            return error(HttpStatus.CONFLICT, ErrorTag.BOOKING_NOT_PAYABLE);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BookingStatusResponse(bookingId, status));
    }

    private static ResponseEntity<Object> error(HttpStatus status, ErrorTag tag) {
        return ResponseEntity.status(status).body(ErrorResponse.from(tag));
    }

    // 같은 키의 다른 본문을 구분하는 요청 해시(SHA-256 hex)
    private String hash(PaymentConfirmRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("승인 요청 해시를 만들 수 없습니다", e);
        }
    }

    /**
     * 승인 요청 1회의 결과. 성공이면 snapshot, 실패면 kind·httpStatus(응답 없으면 null)·결제사 안내 문구.
     */
    private record Attempt(PaymentSnapshot snapshot, PaymentFailureKind kind, Integer httpStatus,
                           String providerMessage) {

        static Attempt of(PaymentSnapshot snapshot) {
            // 200이어도 DONE이 아니면 승인됐다고 보지 않는다(조회로 확인)
            return snapshot.status() == PaymentStatus.DONE ? new Attempt(snapshot, null, 200, null)
                    : new Attempt(null, PaymentFailureKind.UNKNOWN, 200, null);
        }

        boolean approved() {
            return snapshot != null;
        }

        boolean serverError() {
            return httpStatus != null && httpStatus >= 500;
        }
    }
}
