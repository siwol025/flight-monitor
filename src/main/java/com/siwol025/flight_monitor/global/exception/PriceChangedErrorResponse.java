package com.siwol025.flight_monitor.global.exception;

import com.siwol025.flight_monitor.global.exception.custom.OfferPriceChangedException;
import java.math.BigDecimal;

/**
 * 가격 변동(409) 응답. ErrorResponse 형식에 현재 금액·통화를 더한다. 주문 단계에서 바뀐 경우처럼 현재가를 모르면 비어 있다.
 */
public record PriceChangedErrorResponse(
        String tag,
        String message,
        BigDecimal currentAmount,
        String currentCurrency
) {

    public static PriceChangedErrorResponse from(OfferPriceChangedException e) {
        return new PriceChangedErrorResponse(e.getErrorTag().name(), e.getErrorTag().getMessage(),
                e.getCurrentAmount(), e.getCurrentCurrency());
    }
}
