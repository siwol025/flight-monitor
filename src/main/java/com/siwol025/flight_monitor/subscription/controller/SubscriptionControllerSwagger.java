package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.subscription.dto.SubscriptionRequest;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Subscription", description = "항공편 구독 관련 API")
public interface SubscriptionControllerSwagger {

    @Operation(summary = "항공편 구독 신청", description = "외부 항공편 정보를 운영 DB에 동기화하고 구독 레코드를 생성합니다.")
    ResponseEntity<Void> subscribe(User user, SubscriptionRequest request);
}
