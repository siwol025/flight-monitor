package com.siwol025.flight_monitor.fare;

import java.time.Instant;
import lombok.Getter;

/**
 * 업체 호출 한도 초과. {@link #resetAt}은 비지 않으며, 그 시각까지 모든 인스턴스가 호출을 멈춘다.
 */
@Getter
public class FareRateLimitedException extends FareProviderException {

    private final Instant resetAt;

    public FareRateLimitedException(Instant resetAt, String message) {
        super(message);
        this.resetAt = resetAt;
    }
}
