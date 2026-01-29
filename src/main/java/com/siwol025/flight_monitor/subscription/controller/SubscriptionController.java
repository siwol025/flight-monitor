package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.subscription.dto.SubscriptionRequest;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
