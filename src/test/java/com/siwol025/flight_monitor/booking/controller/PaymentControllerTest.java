package com.siwol025.flight_monitor.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.request.PaymentConfirmRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import com.siwol025.flight_monitor.booking.service.PaymentConfirmService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class PaymentControllerTest {

    private static final String KEY = "0b7c1d2e-3f40-4a5b-8c6d-7e8f90a1b2c3";
    private static final String VALID_BODY = """
            {"orderId":"fm-6f1c2c1e","paymentKey":"tviva20261105abc","amount":106456}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentConfirmService paymentConfirmService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        User user = User.builder().email("test@test.com").name("테스트유저").provider(Provider.GOOGLE)
                .providerId("google-id-123").role(Role.USER).build();
        given(jwtProvider.getUserIdFromAccessToken(anyString())).willReturn(1L);
        given(userService.getByUserId(1L)).willReturn(user);
    }

    @Test
    void confirm_인증없음_401() throws Exception {
        mockMvc.perform(post("/api/payments/toss/confirm").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_정상_202() throws Exception {
        given(paymentConfirmService.confirm(any(), eq(KEY),
                eq(new PaymentConfirmRequest("fm-6f1c2c1e", "tviva20261105abc", 106456L))))
                .willReturn(new Fresh<>(ResponseEntity.status(202)
                        .body(new BookingStatusResponse(7L, BookingStatus.PAYMENT_APPROVED))));

        mockMvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.bookingId").value(7))
                .andExpect(jsonPath("$.status").value("PAYMENT_APPROVED"));
    }

    @Test
    void confirm_같은키저장응답_상태와본문그대로() throws Exception {
        given(paymentConfirmService.confirm(any(), eq(KEY), any()))
                .willReturn(new Replay<>(202, "{\"bookingId\":7,\"status\":\"PAYMENT_CONFIRMING\"}"));

        mockMvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_CONFIRMING"));
    }

    @Test
    void confirm_키없음_400() throws Exception {
        mockMvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("IDEMPOTENCY_KEY_REQUIRED"));
        verify(paymentConfirmService, never()).confirm(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"paymentKey\":\"tviva20261105abc\",\"amount\":106456}",
            "{\"orderId\":\"fm-6f1c2c1e\",\"paymentKey\":\" \",\"amount\":106456}",
            "{\"orderId\":\"fm-6f1c2c1e\",\"paymentKey\":\"tviva20261105abc\"}",
            "{\"orderId\":\"fm-6f1c2c1e\",\"paymentKey\":\"tviva20261105abc\",\"amount\":0}"})
    void confirm_요청검증실패_400(String body) throws Exception {
        mockMvc.perform(post("/api/payments/toss/confirm").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("INVALID_INPUT"));
        verify(paymentConfirmService, never()).confirm(any(), any(), any());
    }
}
