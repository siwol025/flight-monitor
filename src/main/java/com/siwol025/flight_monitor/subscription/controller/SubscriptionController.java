package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.subscription.dto.request.SubscriptionRequest;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController implements SubscriptionControllerSwagger{

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<Void> subscribe(
            @Parameter(hidden = true) @LoginUser User user,
            @Valid @RequestBody SubscriptionRequest request
    ) {
        subscriptionService.subscribe(user, request.flightId(), request.seatGrade());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<SubscriptionResponse>> getMySubscriptions(@Parameter(hidden = true) @LoginUser User user) {
        List<SubscriptionResponse> response = subscriptionService.getSubscriptions(user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionDetailResponse> getMySubscription(
            @Parameter(hidden = true) @LoginUser User user,
            @PathVariable Long subscriptionId
    ) {
        SubscriptionDetailResponse response = subscriptionService.getSubscription(user, subscriptionId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> cancelSubscription(@Parameter(hidden = true) @LoginUser User user, @PathVariable Long subscriptionId) {
        subscriptionService.unsubscribe(user, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
