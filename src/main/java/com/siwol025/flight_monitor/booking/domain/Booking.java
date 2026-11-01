package com.siwol025.flight_monitor.booking.domain;

import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.fare.PlacedOrder;
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
 * 구매 내역 1건. 노선·항공편 값은 구매 직전에 재조회한 offer에서, 주문 ID·예약 번호는 업체가 확정한 주문에서 가져온다.
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

    @Column(name = "provider_order_id", nullable = false, length = 100)
    private String providerOrderId;

    @Column(name = "booking_reference", nullable = false, length = 20)
    private String bookingReference;

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

    public Booking(User user, FlightOffer offer, PlacedOrder order, Passenger passenger) {
        this.user = user;
        this.offerId = offer.offerId();
        this.providerOrderId = order.orderId();
        this.bookingReference = order.bookingReference();
        this.departureAirport = offer.departureAirport();
        this.arrivalAirport = offer.arrivalAirport();
        this.departureDate = offer.departingAt().toLocalDate();
        this.seatGrade = offer.seatGrade();
        this.carrierCode = offer.carrierCode();
        this.carrierName = offer.carrierName();
        this.flightNumber = offer.flightNumber();
        this.departingAt = offer.departingAt();
        this.arrivingAt = offer.arrivingAt();
        this.amount = order.amount();
        this.currency = order.currency();
        this.passengerTitle = passenger.title();
        this.givenName = passenger.givenName();
        this.familyName = passenger.familyName();
        this.passengerGender = passenger.gender();
        this.bornOn = passenger.bornOn();
        this.email = passenger.email();
        this.phoneNumber = passenger.phoneNumber();
    }
}
