package com.siwol025.flight_monitor.global.exception.custom;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import java.math.BigDecimal;

/**
 * 구매 요청 금액·통화가 현재 가격과 다름. 재조회 단계에서는 현재 금액·통화를 담고, 주문 단계(재조회 뒤 바뀜)에서는
 * 현재가를 모르므로 비워 둔다(업체를 다시 부르지 않는다).
 */
public class OfferPriceChangedException extends ConflictException {

    private final BigDecimal currentAmount;
    private final String currentCurrency;

    public OfferPriceChangedException(BigDecimal currentAmount, String currentCurrency) {
        super(ErrorTag.OFFER_PRICE_CHANGED);
        this.currentAmount = currentAmount;
        this.currentCurrency = currentCurrency;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public String getCurrentCurrency() {
        return currentCurrency;
    }
}
