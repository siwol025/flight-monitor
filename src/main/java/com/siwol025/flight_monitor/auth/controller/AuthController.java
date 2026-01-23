package com.siwol025.flight_monitor.auth.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.auth.dto.request.LoginRequest;
import com.siwol025.flight_monitor.auth.dto.request.RefreshTokenRequest;
import com.siwol025.flight_monitor.auth.dto.response.LoginResponse;
import com.siwol025.flight_monitor.auth.dto.response.RefreshTokenResponse;
import com.siwol025.flight_monitor.auth.service.AuthService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthController implements AuthControllerSwagger{

    private final AuthService authService;

    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request, Provider.GOOGLE));
    }

    @PostMapping("/token")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Parameter(hidden = true) @LoginUser User user) {
        authService.logout(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/token/verification")
    public ResponseEntity<Void> verify(@Parameter(hidden = true) @LoginUser User user) {
        return ResponseEntity.noContent().build();
    }
}
