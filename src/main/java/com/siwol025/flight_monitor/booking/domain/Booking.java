package com.siwol025.flight_monitor.booking.domain;

import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.global.entity.BaseTimeEntity;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예매 1건. 노선·항공편 값은 구매 직전에 재조회한 offer에서, 주문 ID·예약 번호는 업체가 만든 hold 주문에서 가져온다.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "offer_id", nullable = false, length = 100)
    private String offerId;

    @Column(name = "provider_order_id", length = 100)
    private String providerOrderId;

    @Column(name = "booking_reference", length = 20)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Column(name = "provider_passenger_id", length = 50)
    private String providerPassengerId;

    /** hold 결제 기한(UTC) */
    @Column(name = "payment_required_by")
    private LocalDateTime paymentRequiredBy;

    /** 고객 결제 금액(KRW 정수). amount·currency는 Duffel 금액이다 */
    @Column(name = "charge_amount")
    private Long chargeAmount;

    @Column(name = "toss_order_id", unique = true, length = 64)
    private String tossOrderId;

    @Column(name = "toss_idempotency_key", length = 64)
    private String tossIdempotencyKey;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(name = "toss_approved_at")
    private LocalDateTime tossApprovedAt;

    @Column(name = "duffel_paid_at")
    private LocalDateTime duffelPaidAt;

    @Column(name = "ticket_numbers")
    private String ticketNumbers;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "departure_airport", nullable = false, length = 3)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 3)
    private String arrivalAirport;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_grade", nullable = false)
    private SeatGrade seatGrade;

    @Column(name = "carrier_code", nullable = false, length = 10)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false)
    private String carrierName;

    @Column(name = "flight_number", nullable = false, length = 20)
    private String flightNumber;

    @Column(name = "departing_at", nullable = false)
    private LocalDateTime departingAt;

    @Column(name = "arriving_at", nullable = false)
    private LocalDateTime arrivingAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "passenger_title", nullable = false)
    private PassengerTitle passengerTitle;

    @Column(name = "given_name", nullable = false, length = 100)
    private String givenName;

    @Column(name = "family_name", nullable = false, length = 100)
    private String familyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "passenger_gender", nullable = false)
    private Gender passengerGender;

    @Column(name = "born_on", nullable = false)
    private LocalDate bornOn;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    /**
     * hold 주문 직전의 예매(HOLD_REQUESTED). 노선·항공편·금액은 구매 직전에 재조회한 offer에서, 업체 승객 ID는 offer의 첫 승객에서
     * 가져온다. 주문 ID·예약 번호·결제 금액은 hold가 생긴 뒤 조건부 UPDATE로 채운다. status_changed_at은 DB 시각으로 따로 남긴다
     */
    public static Booking holdRequested(User user, FlightOffer offer, Passenger passenger) {
        Booking booking = new Booking();
        booking.user = user;
        booking.offerId = offer.offerId();
        booking.status = BookingStatus.HOLD_REQUESTED;
        booking.providerPassengerId = offer.terms().passengerIds().getFirst();
        booking.departureAirport = offer.departureAirport();
        booking.arrivalAirport = offer.arrivalAirport();
        booking.departureDate = offer.departingAt().toLocalDate();
        booking.seatGrade = offer.seatGrade();
        booking.carrierCode = offer.carrierCode();
        booking.carrierName = offer.carrierName();
        booking.flightNumber = offer.flightNumber();
        booking.departingAt = offer.departingAt();
        booking.arrivingAt = offer.arrivingAt();
        booking.amount = offer.amount();
        booking.currency = offer.currency();
        booking.passengerTitle = passenger.title();
        booking.givenName = passenger.givenName();
        booking.familyName = passenger.familyName();
        booking.passengerGender = passenger.gender();
        booking.bornOn = passenger.bornOn();
        booking.email = passenger.email();
        booking.phoneNumber = passenger.phoneNumber();
        return booking;
    }
}
