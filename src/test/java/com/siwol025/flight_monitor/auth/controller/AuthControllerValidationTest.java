package com.siwol025.flight_monitor.auth.controller;

import com.siwol025.flight_monitor.auth.service.AuthService;
import com.siwol025.flight_monitor.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthService authService = Mockito.mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_idToken_null이면_400_반환() throws Exception {
        mockMvc.perform(post("/api/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_idToken_빈문자열이면_400_반환() throws Exception {
        mockMvc.perform(post("/api/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
