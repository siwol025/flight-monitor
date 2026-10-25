package com.siwol025.flight_monitor.mock.duffel;

import com.siwol025.flight_monitor.mock.duffel.MockDuffelRateLimitEnforcer.RateLimitStatus;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Duffel 호환 운임 검색 엔드포인트(와이어 스텁). 운영 어댑터가 base-url만 바꿔 이곳을 부른다.
 *
 * <p>Authorization 헤더는 검사하지 않는다. compose(mock 환경)에는 토큰이 없어 어댑터가 빈 Bearer를 보내므로,
 * 검사하면 mock 환경 전체가 401로 멈춘다. 쿼리 return_offers·supplier_timeout도 받기만 하고 무시한다.</p>
 */
@RestController
public class MockDuffelOfferController {

    private final MockDuffelOfferService mockDuffelOfferService;
    private final MockDuffelRateLimitEnforcer rateLimitEnforcer;
    private final Clock clock;

    public MockDuffelOfferController(MockDuffelOfferService mockDuffelOfferService,
                                     MockDuffelRateLimitEnforcer rateLimitEnforcer,
                                     ObjectProvider<Clock> clock) {
        this.mockDuffelOfferService = mockDuffelOfferService;
        this.rateLimitEnforcer = rateLimitEnforcer;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @PostMapping("/air/offer_requests")
    public ResponseEntity<?> searchOffers(@RequestBody MockDuffelOfferRequest request) {
        RateLimitStatus rateLimit = rateLimitEnforcer.acquire(clock.instant());
        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(rateLimit.toHeaders())
                    .body(MockDuffelErrorResponse.rateLimitExceeded());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(rateLimit.toHeaders())
                .body(mockDuffelOfferService.search(request));
    }
}
