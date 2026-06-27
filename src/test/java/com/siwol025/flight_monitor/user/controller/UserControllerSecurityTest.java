package com.siwol025.flight_monitor.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class UserControllerSecurityTest {

    @Test
    @DisplayName("GET /login/success?token= 엔드포인트가 존재하지 않아야 한다")
    void loginSuccess_endpoint_must_not_exist() {
        List<String> violatingMethods = Arrays.stream(UserController.class.getDeclaredMethods())
                .filter(this::mapsToLoginSuccess)
                .map(Method::getName)
                .toList();

        if (!violatingMethods.isEmpty()) {
            fail("loginSuccess 메서드가 제거되어야 합니다. " +
                    "GET /login/success?token= 엔드포인트는 JWT를 URL 파라미터로 노출하는 보안 취약점입니다. " +
                    "발견된 메서드: " + violatingMethods);
        }
    }

    @Test
    @DisplayName("GET /api/me 반환 타입이 ResponseEntity<UserResponse>이어야 한다")
    void getMyInfo_반환타입이_UserResponse이어야한다() {
        Method targetMethod = Arrays.stream(UserController.class.getDeclaredMethods())
                .filter(m -> {
                    GetMapping mapping = m.getAnnotation(GetMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains("/api/me");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("GET /api/me 매핑 메서드를 찾을 수 없습니다"));

        String returnTypeName = targetMethod.getGenericReturnType().getTypeName();

        if (!returnTypeName.contains("UserResponse")) {
            fail("getMyInfo() 반환 타입이 ResponseEntity<UserResponse>이어야 합니다. " +
                 "현재 반환 타입: " + returnTypeName +
                 " — User 엔티티를 직접 반환하면 민감 필드(providerId, provider, role)가 노출됩니다.");
        }
    }

    private boolean mapsToLoginSuccess(Method method) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            for (String path : getMapping.value()) {
                if (path.contains("/login/success")) {
                    return true;
                }
            }
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null) {
            boolean isGet = Arrays.asList(requestMapping.method()).contains(RequestMethod.GET);
            if (isGet) {
                for (String path : requestMapping.value()) {
                    if (path.contains("/login/success")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
