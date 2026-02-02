package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.subscription.dto.request.SubscriptionRequest;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestBody SubscriptionRequest request
    ) {
        subscriptionService.subscribe(user, request.flightId(), request.seatGrade());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<SubscriptionResponse>> getMySubscriptions(@Parameter(hidden = true) @LoginUser User user) {
        List<SubscriptionResponse> response = subscriptionService.getSubscriptions(user);
        return ResponseEntity.ok(response);
    }
}
