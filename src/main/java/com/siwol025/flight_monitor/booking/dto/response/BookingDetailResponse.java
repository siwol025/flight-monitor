package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 예매 1건의 진행 상태. amount·currency는 업체 금액, chargeAmount는 고객 결제 금액(원)이다.
 * HELD(고객 결제 대기)일 때만 Toss 결제창에 넘길 값(payment)을 담는다. ticketNumbers는 발권 전이면 빈 목록이다.
 */
public record BookingDetailResponse(
        Long bookingId,
        BookingStatus status,
        String offerId,
        String orderId,
        String bookingReference,
        List<String> ticketNumbers,
        String departureAirport,
        String arrivalAirport,
        LocalDate departureDate,
        SeatGrade seatGrade,
        String carrierCode,
        String carrierName,
        String flightNumber,
        LocalDateTime departingAt,
        LocalDateTime arrivingAt,
        BigDecimal amount,
        String currency,
        Long chargeAmount,
        Instant paymentRequiredBy,
        String passengerName,
        LocalDateTime createdAt,
        Payment payment
) {

    /**
     * Toss 결제창(payment({customerKey}).requestPayment) 값. 시크릿 키는 담지 않는다.
     *
     * @param customerKey 결제창 구매자 식별 값(추측할 수 없는 무작위 값)
     * @param orderId     이 예매의 Toss 주문번호
     */
    public record Payment(String clientKey, String customerKey, String orderId, String orderName, long amount,
                          String currency) {
    }

    public static BookingDetailResponse from(Booking booking, String tossClientKey) {
        Payment payment = booking.getStatus() != BookingStatus.HELD ? null
                : new Payment(tossClientKey, "fmc_" + UUID.randomUUID().toString().replace("-", ""),
                        booking.getTossOrderId(), orderName(booking), booking.getChargeAmount(), "KRW");
        return new BookingDetailResponse(booking.getId(), booking.getStatus(), booking.getOfferId(),
                booking.getProviderOrderId(), booking.getBookingReference(), ticketNumbers(booking),
                booking.getDepartureAirport(),
                booking.getArrivalAirport(), booking.getDepartureDate(), booking.getSeatGrade(),
                booking.getCarrierCode(), booking.getCarrierName(), booking.getFlightNumber(),
                booking.getDepartingAt(), booking.getArrivingAt(), booking.getAmount(), booking.getCurrency(),
                booking.getChargeAmount(),
                booking.getPaymentRequiredBy() == null ? null : booking.getPaymentRequiredBy().toInstant(ZoneOffset.UTC),
                booking.getGivenName() + " " + booking.getFamilyName(), booking.getCreatedAt(), payment);
    }

    // 쉼표로 이어 저장한 값을 목록으로 되돌린다
    private static List<String> ticketNumbers(Booking booking) {
        String stored = booking.getTicketNumbers();
        return stored == null || stored.isBlank() ? List.of() : Arrays.asList(stored.split(","));
    }

    // 예: ICN-NRT ZZ6258
    private static String orderName(Booking booking) {
        return booking.getDepartureAirport() + "-" + booking.getArrivalAirport() + " " + booking.getCarrierCode()
                + booking.getFlightNumber();
    }
}
