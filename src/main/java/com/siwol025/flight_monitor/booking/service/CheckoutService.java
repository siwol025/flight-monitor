package com.siwol025.flight_monitor.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.request.PassengerRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingDetailResponse;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyClaim;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyScope;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyService;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOrder;
import com.siwol025.flight_monitor.fare.FlightOrderException;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.HoldOrderCommand;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.PriceChangedErrorResponse;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.ConflictException;
import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import com.siwol025.flight_monitor.global.exception.custom.OfferPriceChangedException;
import com.siwol025.flight_monitor.global.exception.custom.ServiceUnavailableException;
import com.siwol025.flight_monitor.user.domain.User;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구매(④): Idempotency-Key 선점 → offer 재조회·재검증 → 예매 행 HOLD_REQUESTED(+키에 예매 ID) → Duffel hold 주문 →
 * HELD(고객 결제 금액·Toss 주문번호·Toss 멱등키 고정).
 *
 * <p>hold 주문(POST)을 보낸 뒤 결과를 모르면 다시 보내지 않는다. 우리 예매 ID를 남긴 주문이 있는지 읽기 조회
 * (offer_id 목록)로 한 번만 확인하고, 없으면 HOLD_REQUESTED로 보류한 채 202를 저장해 돌려준다(이후 확인은 스케줄러).
 * 503(키 행 삭제, 같은 키 재처리)은 hold가 만들어지지 않았다고 확실한 경우에만 쓴다.</p>
 *
 * <p>외부 호출은 DB 트랜잭션 밖에서 하고, 상태 변경은 조건부 UPDATE로만 한다(서버가 여럿이어도 한 곳만 바꾼다).</p>
 */
@Slf4j
@Service
public class CheckoutService {

    private static final int ADULT_AGE = 18;

    private final FlightOfferFinder flightOfferFinder;
    private final FlightOrderGateway flightOrderGateway;
    private final IdempotencyService idempotencyService;
    private final BookingStateWriter bookingStateWriter;
    private final BookingRepository bookingRepository;
    private final BookingPolicy bookingPolicy;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String tossClientKey;

    public CheckoutService(FlightOfferFinder flightOfferFinder, FlightOrderGateway flightOrderGateway,
                           IdempotencyService idempotencyService, BookingStateWriter bookingStateWriter,
                           BookingRepository bookingRepository, BookingPolicy bookingPolicy,
                           PlatformTransactionManager transactionManager, ObjectMapper objectMapper, Clock clock,
                           @Value("${payment.toss.client-key:}") String tossClientKey) {
        this.flightOfferFinder = flightOfferFinder;
        this.flightOrderGateway = flightOrderGateway;
        this.idempotencyService = idempotencyService;
        this.bookingStateWriter = bookingStateWriter;
        this.bookingRepository = bookingRepository;
        this.bookingPolicy = bookingPolicy;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tossClientKey = tossClientKey;
    }

    /** 이 메서드는 트랜잭션 밖에서 부른다(키 선점·결과 저장·상태 변경이 각자 바로 커밋돼야 다른 서버가 본다) */
    public IdempotentResult<Object> purchase(User user, String idempotencyKey, BookingRequest request) {
        return idempotencyService.execute(user.getId(), IdempotencyScope.BOOKING_CREATE, idempotencyKey,
                hash(request), claim -> process(user, request, claim));
    }

    private ResponseEntity<Object> process(User user, BookingRequest request, IdempotencyClaim claim) {
        if (claim.bookingId() != null) {
            // 멈춘 처리를 이어받았고 그 처리가 예매를 이미 만들었다: 외부 호출 없이 현재 상태만 응답한다(확인은 스케줄러)
            return currentState(claim.bookingId());
        }
        FlightOffer offer;
        Passenger passenger;
        try {
            offer = validatedOffer(request);
            passenger = toPassenger(request.passenger());
        } catch (OfferPriceChangedException e) {
            return ResponseEntity.status(e.getStatus()).body(PriceChangedErrorResponse.from(e));
        } catch (HttpStatusException e) {
            // 확정 4xx는 FAILED로, 업체 일시 장애(503, hold 전)는 키 삭제로 저장되도록 예외가 아니라 응답으로 돌려준다
            return error(e.getStatus(), e.getErrorTag());
        }
        long bookingId = insertHoldRequested(user, offer, passenger, claim);
        return hold(bookingId, offer, passenger);
    }

    // 재조회 → 예매 가능(허용 항공사·hold 가능) → 만료 임박 → 성인 → 금액·통화 순으로 확인한다
    private FlightOffer validatedOffer(BookingRequest request) {
        FlightOffer offer = findOffer(request.offerId())
                .orElseThrow(() -> new ConflictException(ErrorTag.OFFER_UNAVAILABLE));
        if (!bookingPolicy.bookable(offer)) {
            throw new ConflictException(ErrorTag.OFFER_NOT_HOLDABLE);
        }
        if (bookingPolicy.expiresSoon(offer, clock.instant())) {
            throw new ConflictException(ErrorTag.OFFER_EXPIRED);
        }
        // 성인 여부는 출발일을 알아야 판정할 수 있어 재조회한 offer로 본다(생일이 출발일이면 그날 만 18세)
        if (request.passenger().bornOn().plusYears(ADULT_AGE).isAfter(offer.departingAt().toLocalDate())) {
            throw new BadRequestException(ErrorTag.PASSENGER_NOT_ADULT);
        }
        // 금액은 표기 자릿수와 상관없이 값으로 비교한다(76.04 = 76.040)
        if (request.amount().compareTo(offer.amount()) != 0 || !offer.currency().equals(request.currency())) {
            throw new OfferPriceChangedException(offer.amount(), offer.currency());
        }
        return offer;
    }

    // 재조회는 읽기라서 업체 장애는 hold 전 일시 오류(503)다
    private Optional<FlightOffer> findOffer(String offerId) {
        try {
            return flightOfferFinder.findOffer(offerId);
        } catch (FareProviderException e) {
            throw new ServiceUnavailableException(ErrorTag.FARE_PROVIDER_UNAVAILABLE);
        }
    }

    // 예매 행 INSERT와 키의 예매 ID 기록은 한 트랜잭션이다(크래시 뒤 재선점한 요청이 이 예매를 이어받는다)
    private long insertHoldRequested(User user, FlightOffer offer, Passenger passenger, IdempotencyClaim claim) {
        Long bookingId = transactionTemplate.execute(status -> {
            long id = bookingRepository.save(Booking.holdRequested(user, offer, passenger)).getId();
            bookingRepository.stampStatusChangedAt(id);
            idempotencyService.attachBooking(claim, id);
            return id;
        });
        return bookingId;
    }

    private ResponseEntity<Object> hold(long bookingId, FlightOffer offer, Passenger passenger) {
        FlightOrder order;
        try {
            order = flightOrderGateway.createHoldOrder(new HoldOrderCommand(offer.offerId(),
                    offer.terms().passengerIds().getFirst(), passenger, String.valueOf(bookingId)));
        } catch (FlightOrderException e) {
            return holdFailed(bookingId, offer.offerId(), e);
        } catch (RuntimeException e) {
            // 201을 받고 본문을 해석하지 못한 경우 등: hold가 이미 생겼을 수 있으므로 결과 불명확과 같게 처리한다
            log.warn("[CheckoutService] hold 결과를 해석하지 못함(재요청하지 않고 조회로 확인): bookingId={}", bookingId, e);
            return resolveUnknownHold(bookingId, offer.offerId());
        }
        return held(bookingId, order);
    }

    private ResponseEntity<Object> holdFailed(long bookingId, String offerId, FlightOrderException e) {
        return switch (e.getKind()) {
            case UNKNOWN -> {
                log.warn("[CheckoutService] hold 결과 불명확(재요청하지 않고 조회로 확인): bookingId={}, code={}",
                        bookingId, e.getCode(), e);
                yield resolveUnknownHold(bookingId, offerId);
            }
            // 업체가 만들지 않았다고 확실한 일시 장애: 예매는 끝내고 키를 지워 같은 키로 다시 처리하게 한다
            case NOT_SENT, RATE_LIMITED, NOT_CREATED -> fail(bookingId, e, HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorTag.FARE_PROVIDER_UNAVAILABLE);
            case OFFER_UNAVAILABLE -> fail(bookingId, e, HttpStatus.CONFLICT, ErrorTag.OFFER_UNAVAILABLE);
            case OFFER_EXPIRED -> fail(bookingId, e, HttpStatus.CONFLICT, ErrorTag.OFFER_EXPIRED);
            case OFFER_ALREADY_BOOKED -> fail(bookingId, e, HttpStatus.CONFLICT, ErrorTag.OFFER_ALREADY_BOOKED);
            case REJECTED -> fail(bookingId, e, HttpStatus.BAD_REQUEST, ErrorTag.ORDER_REJECTED);
            case CONFIGURATION -> {
                log.error("[CheckoutService] hold 요청이 설정 문제로 거부됨: bookingId={}, code={}", bookingId,
                        e.getCode(), e);
                yield fail(bookingId, e, HttpStatus.SERVICE_UNAVAILABLE, ErrorTag.FARE_PROVIDER_UNAVAILABLE);
            }
        };
    }

    // hold가 만들어지지 않았다고 확인된 실패. 원인 code를 남기고 예매를 끝낸다
    private ResponseEntity<Object> fail(long bookingId, FlightOrderException e, HttpStatus status, ErrorTag tag) {
        bookingStateWriter.failHold(bookingId, e.getCode() != null ? e.getCode() : e.getKind().name());
        return error(status, tag);
    }

    // POST를 다시 보내지 않고 읽기 조회 1회로만 확인한다. 우리 예매 ID를 남긴 주문이 없거나 조회도 실패하면 보류(202)
    private ResponseEntity<Object> resolveUnknownHold(long bookingId, String offerId) {
        String ourBookingId = String.valueOf(bookingId);
        try {
            Optional<FlightOrder> found = flightOrderGateway.findOrdersByOffer(offerId).stream()
                    .filter(order -> ourBookingId.equals(order.bookingId()))
                    .findFirst();
            if (found.isPresent()) {
                return held(bookingId, found.get());
            }
        } catch (RuntimeException e) {
            log.warn("[CheckoutService] hold 확인 조회 실패(보류, 스케줄러가 다시 확인): bookingId={}", bookingId, e);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BookingStatusResponse(bookingId, BookingStatus.HOLD_REQUESTED));
    }

    private ResponseEntity<Object> held(long bookingId, FlightOrder order) {
        Long chargeAmount = bookingPolicy.chargeAmount(order.amount(), order.currency());
        if (chargeAmount == null) {
            // 예매 가능 판정에서 USD만 받으므로 정상 경로에서는 없다. 보류하고 원인을 남긴다
            log.error("[CheckoutService] hold 주문 통화의 환율을 모름(보류): bookingId={}, currency={}", bookingId,
                    order.currency());
            return currentState(bookingId);
        }
        // 0행이면 다른 서버(확인 스케줄러)가 먼저 바꿨다. 그 결과를 그대로 응답한다
        bookingStateWriter.hold(bookingId, order.orderId(), order.bookingReference(), order.paymentRequiredBy(),
                chargeAmount, "fm-" + UUID.randomUUID(), UUID.randomUUID().toString());
        return currentState(bookingId);
    }

    // HELD면 결제창 값과 함께 201, 아직 결과를 모르는 상태면 202
    private ResponseEntity<Object> currentState(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        if (booking.getStatus() == BookingStatus.HELD) {
            return ResponseEntity.status(HttpStatus.CREATED).body(BookingDetailResponse.from(booking, tossClientKey));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new BookingStatusResponse(bookingId, booking.getStatus()));
    }

    private static ResponseEntity<Object> error(HttpStatus status, ErrorTag tag) {
        return ResponseEntity.status(status).body(ErrorResponse.from(tag));
    }

    // 같은 키의 다른 본문을 구분하는 요청 해시(SHA-256 hex)
    private String hash(BookingRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("구매 요청 해시를 만들 수 없습니다", e);
        }
    }

    // 요청은 업체 형식(소문자)으로 받아 검증하고, 포트에는 enum으로 넘긴다
    private static Passenger toPassenger(PassengerRequest request) {
        return new Passenger(PassengerTitle.valueOf(request.title().toUpperCase(Locale.ROOT)), request.givenName(),
                request.familyName(), Gender.valueOf(request.gender().toUpperCase(Locale.ROOT)), request.bornOn(),
                request.email(), request.phoneNumber());
    }
}
