package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.subscription.dto.request.SubscriptionRequest;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Subscription", description = "항공편 구독 관련 API")
public interface SubscriptionControllerSwagger {

    @Operation(
            summary = "항공편 구독 생성",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "구독 성공"
                    )
            }
    )
    ResponseEntity<Void> subscribe(User user, SubscriptionRequest request);

    @Operation(
            summary = "회원별 구독 목록 조회",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<List<SubscriptionResponse>> getMySubscriptions(User user);

    @Operation(
            summary = "구독 내역 상세 조회",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<SubscriptionDetailResponse> getMySubscription(Long subscriptionId);

    @Operation(
            summary = "구독 취소",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "삭제 성공"
                    )
            }
    )
    ResponseEntity<Void> cancelSubscription(User user, Long subscriptionId);
}