package com.siwol025.flight_monitor.user.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/api/me")
    public ResponseEntity<User> getMyInfo(@Parameter(hidden = true) @LoginUser User user) {
        return ResponseEntity.ok(user);
    }

}
