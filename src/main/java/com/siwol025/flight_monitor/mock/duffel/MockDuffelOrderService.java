package com.siwol025.flight_monitor.mock.duffel;

import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.Seat;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duffel 형식 주문: offer(좌석가격 행)의 빈 좌석 하나를 예약한다. 주문 테이블은 없고 주문 ID는 좌석 ID에서 만든다.
 */
@Service
@RequiredArgsConstructor
public class MockDuffelOrderService {

    private static final String ORDER_ID_PREFIX = "ord_mock_";

    private final MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    private final MockSeatRepository mockSeatRepository;

    // 동시성 처리 안 함(의도): 빈 좌석 조회 → reserve() → 더티 체킹
    @Transactional
    public OrderResult order(MockDuffelOrderRequest request) {
        MockDuffelOrderRequest.Data data = request.data();
        Optional<FlightSeatPrice> found = MockDuffelOfferService.parsePriceId(data.selectedOffers().getFirst())
                .flatMap(mockFlightSeatPriceRepository::findWithFlightDetailsById);
        if (found.isEmpty()) {
            return new OrderResult.NotFound();
        }
        FlightSeatPrice price = found.get();
        MockDuffelOfferResponse.Offer offer = MockDuffelOfferService.toOffer(price.getFlight(), price);
        if (!matchesCurrentPrice(data.payments().getFirst(), price.getPrice(), offer.totalCurrency())) {
            return new OrderResult.PriceChanged();
        }
        Optional<Seat> emptySeat = mockSeatRepository.findFirstByFlightIdAndSeatGradeAndIsBookedFalseOrderBySeatNumberAsc(
                price.getFlight().getId(), price.getSeatGrade());
        if (emptySeat.isEmpty()) {
            return new OrderResult.SoldOut();
        }
        Seat seat = emptySeat.get();
        seat.reserve();

        return new OrderResult.Placed(new MockDuffelOrderResponse(new MockDuffelOrderResponse.Data(
                ORDER_ID_PREFIX + seat.getId(), bookingReference(seat.getId()), offer.totalAmount(),
                offer.totalCurrency(), data.passengers(), offer.slices())));
    }

    /**
     * 주문 결과. 컨트롤러가 Duffel 상태 코드로 바꾼다(전역 예외 처리기는 앱 오류 형식이라 쓰지 않는다).
     */
    public sealed interface OrderResult {
        record Placed(MockDuffelOrderResponse response) implements OrderResult {
        }

        record SoldOut() implements OrderResult {
        }

        record PriceChanged() implements OrderResult {
        }

        record NotFound() implements OrderResult {
        }
    }

    // 금액은 표기와 상관없이 값으로 비교한다(250000 = 250000.00). 숫자가 아닌 금액도 현재 가격과 다른 것으로 본다
    // (정상 경로에서는 어댑터가 숫자만 보낸다)
    private static boolean matchesCurrentPrice(MockDuffelOrderRequest.Payment payment, BigDecimal currentPrice,
                                               String currentCurrency) {
        if (!currentCurrency.equals(payment.currency())) {
            return false;
        }
        try {
            return new BigDecimal(payment.amount()).compareTo(currentPrice) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 실제 PNR처럼 영숫자 6자. 좌석 ID에서 만들므로 결정적이다
    private static String bookingReference(Long seatId) {
        String base36 = Long.toString(seatId, 36).toUpperCase();
        return "0".repeat(Math.max(0, 6 - base36.length())) + base36;
    }
}
