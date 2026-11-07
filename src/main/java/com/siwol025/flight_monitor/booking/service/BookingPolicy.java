package com.siwol025.flight_monitor.booking.service;

import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.OfferTerms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * offer를 우리 흐름(hold 주문 → 고객 결제 → hold 결제)으로 예매할 수 있는지와 고객 결제 금액(원)을 정한다.
 * 재조회 응답(②)과 구매(④)가 같은 규칙을 쓴다.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(BookingProperties.class)
public class BookingPolicy {

    private static final String USD = "USD";

    private final BookingProperties properties;

    /**
     * 허용 항공사이고, 결제를 미룰 수 있고(hold), 주문에 넣을 승객 ID가 있고, 환율을 아는 통화(USD)일 때만 예매할 수 있다.
     * 만료 임박은 시각에 따라 바뀌므로 {@link #expiresSoon}으로 따로 본다
     */
    public boolean bookable(FlightOffer offer) {
        OfferTerms terms = offer.terms();
        return properties.getAllowedOwnerCodes().contains(terms.ownerCode())
                && !terms.requiresInstantPayment()
                && !terms.passengerIds().isEmpty()
                && USD.equals(offer.currency());
    }

    /** 만료 시각을 모르면 업체가 주문에서 막으므로 여기서는 막지 않는다 */
    public boolean expiresSoon(FlightOffer offer, Instant now) {
        Instant expiresAt = offer.terms().expiresAt();
        return expiresAt != null && !now.plus(properties.getOfferExpiryBuffer()).isBefore(expiresAt);
    }

    /** 고객 결제 금액(원 정수, 올림). 환율을 모르는 통화면 null */
    public Long chargeAmount(BigDecimal amount, String currency) {
        if (!USD.equals(currency)) {
            return null;
        }
        return amount.multiply(properties.getUsdToKrwRate()).setScale(0, RoundingMode.CEILING).longValueExact();
    }
}
