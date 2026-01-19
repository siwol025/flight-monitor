package com.siwol025.flight_monitor.user.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    // SecurityContextHolder를 직접 조회하지 않습니다!
    @GetMapping("/api/me")
    public ResponseEntity<User> getMyInfo(@LoginUser User user) {
        return ResponseEntity.ok(user);
    }

    @GetMapping("/login/success")
    public String loginSuccess(@RequestParam String token) {
        return "로그인 성공! JWT 토큰: " + token;
    }
}
