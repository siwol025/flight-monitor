package com.siwol025.flight_monitor.booking.service;

import com.siwol025.flight_monitor.booking.dto.response.BookingDetailResponse;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.booking.dto.response.FlightOfferResponse;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.ConflictException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.global.exception.custom.ServiceUnavailableException;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.user.domain.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예매용 항공권 검색·결제 직전 재조회와 내 구매 내역·예매 조회. 구매(hold 주문)는 {@link CheckoutService}가 한다.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final FlightOfferFinder flightOfferFinder;
    private final BookingPolicy bookingPolicy;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    // 결제 대기(HELD) 예매의 결제창 값에 담는 공개 클라이언트 키
    @Value("${payment.toss.client-key:}")
    private String tossClientKey;

    // 노선 값 검증(공항 코드, 과거 출발일, 같은 공항)은 구독과 같은 노선 키 규칙을 쓴다
    public List<FlightOfferResponse> searchOffers(String departureAirport, String arrivalAirport,
                                                  LocalDate departureDate, SeatGrade seatGrade) {
        RouteKey routeKey = RouteKey.of(departureAirport, arrivalAirport, departureDate, seatGrade, clock);
        return callProvider(() -> flightOfferFinder.searchOffers(routeKey)).stream()
                .map(this::toResponse)
                .toList();
    }

    // 결제 직전 재조회: 사용자는 이 금액·원화 예상 금액·만료 시각을 보고 승객 정보를 입력한 뒤 구매를 누른다
    public FlightOfferResponse getOffer(String offerId) {
        return callProvider(() -> flightOfferFinder.findOffer(offerId))
                .map(this::toResponse)
                .orElseThrow(() -> new ConflictException(ErrorTag.OFFER_UNAVAILABLE));
    }

    private FlightOfferResponse toResponse(FlightOffer offer) {
        return FlightOfferResponse.of(offer, bookingPolicy.bookable(offer),
                bookingPolicy.chargeAmount(offer.amount(), offer.currency()));
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

    // 진행 상태 조회(⑥ 폴링). 남의 예매는 없는 예매와 같이 404다
    @Transactional(readOnly = true)
    public BookingDetailResponse getBooking(User user, long bookingId) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> booking.getUser().getId().equals(user.getId()))
                .map(booking -> BookingDetailResponse.from(booking, tossClientKey))
                .orElseThrow(() -> new NotFoundException(ErrorTag.BOOKING_NOT_FOUND));
    }
}
