package com.siwol025.flight_monitor.mock.duffel;

import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * mock 항공편 테이블을 노선·날짜·등급으로 걸러 Duffel 형식 운임 목록으로 바꾼다. 항공편 하나가 운임 하나(편도 1구간)다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockDuffelOfferService {

    // mock 가격 데이터에는 통화가 없다. 실제 Duffel 응답도 USD라 mock ↔ Duffel 전환 때 통화가 바뀌지 않는다
    private static final String CURRENCY = "USD";
    // 실제 응답과 같은 형식(초 포함, 오프셋 없음): 2026-11-26T19:21:00
    private static final DateTimeFormatter DUFFEL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String OFFER_ID_PREFIX = "off_mock_";
    // 18자리까지만 받아 long 범위를 넘는 숫자는 형식 오류(없음)로 본다
    private static final Pattern OFFER_ID = Pattern.compile(OFFER_ID_PREFIX + "(\\d{1,18})");

    private final MockFlightRepository mockFlightRepository;
    private final MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    private final MockSeatRepository mockSeatRepository;

    public MockDuffelOfferResponse search(MockDuffelOfferRequest request) {
        MockDuffelOfferRequest.Slice slice = request.data().slices().getFirst();
        SeatGrade seatGrade = seatGrade(request.data().cabinClass());
        LocalDate date = slice.departureDate();
        // 하루의 끝은 소수 초 없는 23:59:59로 둔다. LocalTime.MAX(…59.999999999)는 DB가 컬럼 정밀도로 반올림하면서
        // 다음 날 00:00:00이 되어 자정 출발편이 섞일 수 있다(mock 시각은 분 단위라 23:59:59.x 출발편은 없다)
        List<MockFlight> flights = mockFlightRepository.findBookableFlightsWithDetails(slice.origin(), slice.destination(),
                date.atStartOfDay(), date.atTime(23, 59, 59), seatGrade);
        return new MockDuffelOfferResponse(new MockDuffelOfferResponse.Data(
                flights.stream().map(flight -> toOffer(flight, seatGrade)).toList()));
    }

    // offer ID는 off_mock_{좌석가격 행 ID}: 항공편과 등급을 함께 식별한다
    public OfferLookup findOffer(String offerId) {
        Matcher matcher = OFFER_ID.matcher(offerId);
        if (!matcher.matches()) {
            return new OfferLookup.NotFound();
        }
        Optional<FlightSeatPrice> found = mockFlightSeatPriceRepository.findWithFlightDetailsById(
                Long.parseLong(matcher.group(1)));
        if (found.isEmpty()) {
            return new OfferLookup.NotFound();
        }
        FlightSeatPrice price = found.get();
        if (!mockSeatRepository.existsByFlightIdAndSeatGradeAndIsBookedFalse(price.getFlight().getId(),
                price.getSeatGrade())) {
            return new OfferLookup.SoldOut();
        }
        return new OfferLookup.Found(new MockDuffelOfferDetailResponse(toOffer(price.getFlight(), price)));
    }

    /**
     * 단건 조회 결과. 컨트롤러가 Duffel 상태 코드로 바꾼다(전역 예외 처리기는 앱 오류 형식이라 쓰지 않는다).
     */
    public sealed interface OfferLookup {
        record Found(MockDuffelOfferDetailResponse response) implements OfferLookup {
        }

        record SoldOut() implements OfferLookup {
        }

        record NotFound() implements OfferLookup {
        }
    }

    private static MockDuffelOfferResponse.Offer toOffer(MockFlight flight, SeatGrade seatGrade) {
        FlightSeatPrice price = flight.getFlightSeatPrices().stream()
                .filter(seatPrice -> seatPrice.getSeatGrade() == seatGrade)
                .findFirst().orElseThrow();
        return toOffer(flight, price);
    }

    private static MockDuffelOfferResponse.Offer toOffer(MockFlight flight, FlightSeatPrice price) {
        MockDuffelOfferResponse.Segment segment = new MockDuffelOfferResponse.Segment(
                new MockDuffelOfferResponse.Place(flight.getDepartureAirport().getAirportCode()),
                new MockDuffelOfferResponse.Place(flight.getArrivalAirport().getAirportCode()),
                new MockDuffelOfferResponse.Carrier(flight.getAirline().getAirlineCode(), flight.getAirline().getAirlineName()),
                flightNumberWithoutCarrier(flight),
                flight.getDepartureTime().format(DUFFEL_DATE_TIME),
                flight.getArrivalTime().format(DUFFEL_DATE_TIME));
        // 금액은 Duffel처럼 소수 두 자리 문자열. 가격 컬럼에 scale이 지정되지 않아 셋째 자리가 있을 수 있으므로 반올림한다
        return new MockDuffelOfferResponse.Offer(OFFER_ID_PREFIX + price.getId(),
                price.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(), CURRENCY,
                List.of(new MockDuffelOfferResponse.Slice(List.of(segment))));
    }

    // Duffel은 항공사 코드와 편명 숫자를 따로 준다(ZZ + "1079"). mock 편명이 항공사 코드로 시작하면 떼어 같은 모양으로 맞춘다
    private static String flightNumberWithoutCarrier(MockFlight flight) {
        String flightNumber = flight.getFlightNumber();
        String carrierCode = flight.getAirline().getAirlineCode();
        return flightNumber.startsWith(carrierCode) ? flightNumber.substring(carrierCode.length()) : flightNumber;
    }

    private static SeatGrade seatGrade(String cabinClass) {
        return switch (cabinClass) {
            case "economy" -> SeatGrade.ECONOMY;
            case "business" -> SeatGrade.BUSINESS;
            case "first" -> SeatGrade.FIRST;
            default -> throw new IllegalArgumentException("지원하지 않는 cabin_class: " + cabinClass);
        };
    }
}
