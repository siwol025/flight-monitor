package com.siwol025.flight_monitor.mock.duffel;

import com.siwol025.flight_monitor.mock.duffel.MockDuffelOrderService.OrderResult;
import com.siwol025.flight_monitor.mock.duffel.MockDuffelRateLimitEnforcer.RateLimitStatus;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Duffel 호환 주문 엔드포인트(와이어 스텁). 검색·단건 조회와 같은 한도 예산을 쓴다(Duffel 한도는 API 키 단위).
 */
@RestController
public class MockDuffelOrderController {

    private final MockDuffelOrderService mockDuffelOrderService;
    private final MockDuffelRateLimitEnforcer rateLimitEnforcer;
    private final Clock clock;

    public MockDuffelOrderController(MockDuffelOrderService mockDuffelOrderService,
                                     MockDuffelRateLimitEnforcer rateLimitEnforcer,
                                     ObjectProvider<Clock> clock) {
        this.mockDuffelOrderService = mockDuffelOrderService;
        this.rateLimitEnforcer = rateLimitEnforcer;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @PostMapping("/air/orders")
    public ResponseEntity<?> createOrder(@RequestBody MockDuffelOrderRequest request) {
        RateLimitStatus rateLimit = rateLimitEnforcer.acquire(clock.instant());
        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(rateLimit.toHeaders())
                    .body(MockDuffelErrorResponse.rateLimitExceeded());
        }
        return switch (mockDuffelOrderService.order(request)) {
            case OrderResult.Placed placed -> ResponseEntity.status(HttpStatus.CREATED)
                    .headers(rateLimit.toHeaders())
                    .body(placed.response());
            case OrderResult.SoldOut soldOut -> ResponseEntity.unprocessableEntity()
                    .headers(rateLimit.toHeaders())
                    .body(MockDuffelErrorResponse.offerNoLongerAvailable());
            case OrderResult.PriceChanged priceChanged -> ResponseEntity.unprocessableEntity()
                    .headers(rateLimit.toHeaders())
                    .body(MockDuffelErrorResponse.priceChanged());
            case OrderResult.NotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .headers(rateLimit.toHeaders())
                    .body(MockDuffelErrorResponse.notFound());
        };
    }
}
