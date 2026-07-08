package com.siwol025.flight_monitor.monitor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class AlertConditionChecker {

    public boolean shouldNotify(BigDecimal oldPrice, BigDecimal newPrice,
                                BigDecimal targetPrice, BigDecimal dropThresholdPercent) {
        if (targetPrice == null && dropThresholdPercent == null) {
            return newPrice.compareTo(oldPrice) < 0;
        }
        boolean targetMet = targetPrice != null && newPrice.compareTo(targetPrice) <= 0;
        boolean thresholdMet = dropThresholdPercent != null && isDropRateMet(oldPrice, newPrice, dropThresholdPercent);
        return targetMet || thresholdMet;
    }

    private boolean isDropRateMet(BigDecimal oldPrice, BigDecimal newPrice, BigDecimal dropThresholdPercent) {
        BigDecimal actualDrop = oldPrice.subtract(newPrice)
                .divide(oldPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return actualDrop.compareTo(dropThresholdPercent) >= 0;
    }
}
