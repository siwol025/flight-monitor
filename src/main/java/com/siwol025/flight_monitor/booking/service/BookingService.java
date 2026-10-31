package com.siwol025.flight_monitor.booking.service;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.request.PassengerRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.booking.dto.response.FlightOfferResponse;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOfferPriceChangedException;
import com.siwol025.flight_monitor.fare.FlightOfferUnavailableException;
import com.siwol025.flight_monitor.fare.FlightOrderPlacer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.fare.PlacedOrder;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.ConflictException;
import com.siwol025.flight_monitor.global.exception.custom.OfferPriceChangedException;
import com.siwol025.flight_monitor.global.exception.custom.ServiceUnavailableException;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간소화 예매: 결제 직전 재조회 → 예매 가능·가격 확인 → 업체 주문 → 구매 내역 저장.
 * 외부 호출(재조회·주문)은 트랜잭션 밖에서 하고, 저장은 save 한 번의 자체 트랜잭션이다.
 * 중복 구매 멱등성과 동시 구매 처리는 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int ADULT_AGE = 18;

    private final FlightOfferFinder flightOfferFinder;
    private final FlightOrderPlacer flightOrderPlacer;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    // 노선 값 검증(공항 코드, 과거 출발일, 같은 공항)은 구독과 같은 노선 키 규칙을 쓴다
    public List<FlightOfferResponse> searchOffers(String departureAirport, String arrivalAirport,
                                                  LocalDate departureDate, SeatGrade seatGrade) {
        RouteKey routeKey = RouteKey.of(departureAirport, arrivalAirport, departureDate, seatGrade, clock);
        return callProvider(() -> flightOfferFinder.searchOffers(routeKey)).stream()
                .map(FlightOfferResponse::from)
                .toList();
    }

    // 결제 직전 재조회: 사용자는 이 금액을 보고 구매를 누른다
    public FlightOfferResponse getOffer(String offerId) {
        return callProvider(() -> flightOfferFinder.findOffer(offerId))
                .map(FlightOfferResponse::from)
                .orElseThrow(() -> new ConflictException(ErrorTag.OFFER_UNAVAILABLE));
    }

    public BookingResponse book(User user, BookingRequest request) {
        FlightOffer offer = callProvider(() -> flightOfferFinder.findOffer(request.offerId()))
                .orElseThrow(() -> new ConflictException(ErrorTag.OFFER_UNAVAILABLE));
        // 성인 여부는 출발일을 알아야 판정할 수 있어 재조회한 offer로 본다(생일이 출발일이면 그날 만 18세)
        if (request.passenger().bornOn().plusYears(ADULT_AGE).isAfter(offer.departingAt().toLocalDate())) {
            throw new BadRequestException(ErrorTag.PASSENGER_NOT_ADULT);
        }
        // 금액은 표기 자릿수와 상관없이 값으로 비교한다(250000 = 250000.00)
        if (request.amount().compareTo(offer.amount()) != 0 || !offer.currency().equals(request.currency())) {
            throw new OfferPriceChangedException(offer.amount(), offer.currency());
        }
        Passenger passenger = toPassenger(request.passenger());
        // 금액·통화가 같다고 확인된 뒤에는 업체가 준 표기 그대로 주문한다(업체는 offer 총액과 같아야 받는다)
        PlacedOrder order = placeOrder(offer, passenger);
        try {
            return BookingResponse.from(bookingRepository.save(new Booking(user, offer, order, passenger)));
        } catch (RuntimeException e) {
            // 주문은 이미 확정됐는데 내역이 없다. mock 한정으로 보상하지 않고 수동 대조용으로 남긴다
            log.error("[BookingService] 주문 후 구매 내역 저장 실패: orderId={}, email={}", order.orderId(),
                    passenger.email(), e);
            throw e;
        }
    }

    // 재조회 뒤 주문 사이에 매진될 수 있다
    private PlacedOrder placeOrder(FlightOffer offer, Passenger passenger) {
        try {
            return callProvider(
                    () -> flightOrderPlacer.placeOrder(offer.offerId(), offer.amount(), offer.currency(), passenger));
        } catch (FlightOfferUnavailableException e) {
            throw new ConflictException(ErrorTag.OFFER_UNAVAILABLE);
        } catch (FlightOfferPriceChangedException e) {
            // 바뀐 현재가는 모른다. 다시 조회하지 않고 사용자가 재조회 API로 확인한다
            throw new OfferPriceChangedException(null, null);
        }
    }

    // 업체 장애·한도 초과는 사용자가 기다렸다 다시 시도할 일이라 503으로 알린다. 변환을 이 기능 안에 둬서
    // 다른 기능에서 새는 같은 예외까지 503이 되지 않게 한다
    private static <T> T callProvider(Supplier<T> call) {
        try {
            return call.get();
        } catch (InvalidRouteException e) {
            // 업체 오류의 하위 타입이지만 업체가 노선을 거부한 입력 문제라 400이다(검색에서만 나온다)
            throw new BadRequestException(ErrorTag.INVALID_ROUTE);
        } catch (FareProviderException e) {
            throw new ServiceUnavailableException(ErrorTag.FARE_PROVIDER_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(User user) {
        return bookingRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(BookingResponse::from)
                .toList();
    }

    // 요청은 업체 형식(소문자)으로 받아 검증하고, 포트에는 enum으로 넘긴다
    private static Passenger toPassenger(PassengerRequest request) {
        return new Passenger(PassengerTitle.valueOf(request.title().toUpperCase(Locale.ROOT)), request.givenName(),
                request.familyName(), Gender.valueOf(request.gender().toUpperCase(Locale.ROOT)), request.bornOn(),
                request.email(), request.phoneNumber());
    }
}
