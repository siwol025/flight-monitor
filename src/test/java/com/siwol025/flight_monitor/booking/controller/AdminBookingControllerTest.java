package com.siwol025.flight_monitor.booking.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.response.AdminBookingResponse;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminBookingController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class AdminBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        given(jwtProvider.getUserIdFromAccessToken(anyString())).willReturn(1L);
    }

    @Test
    void admin_list_status필터() throws Exception {
        loginAs(Role.ADMIN);
        given(bookingService.getBookingsByStatus(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED)).willReturn(List.of(
                new AdminBookingResponse(7L, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED,
                        LocalDateTime.of(2026, 11, 5, 1, 10, 30), "ord_0000BC7e5XO0QzobMUjokT", null)));

        mockMvc.perform(get("/api/admin/bookings").param("status", "DUFFEL_PAYMENT_UNCONFIRMED")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(7))
                .andExpect(jsonPath("$[0].status").value("DUFFEL_PAYMENT_UNCONFIRMED"))
                .andExpect(jsonPath("$[0].statusChangedAt").value("2026-11-05T01:10:30"))
                .andExpect(jsonPath("$[0].orderId").value("ord_0000BC7e5XO0QzobMUjokT"))
                .andExpect(jsonPath("$[0].failureCode").doesNotExist());
    }

    @Test
    void admin_list_ADMIN아니면403() throws Exception {
        loginAs(Role.USER);

        mockMvc.perform(get("/api/admin/bookings").param("status", "DUFFEL_PAYMENT_UNCONFIRMED")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.tag").value("ADMIN_ONLY"));
        verify(bookingService, never()).getBookingsByStatus(any());
    }

    @Test
    void admin_list_인증없음_401() throws Exception {
        mockMvc.perform(get("/api/admin/bookings").param("status", "HELD"))
                .andExpect(status().isUnauthorized());
    }

    private void loginAs(Role role) {
        given(userService.getByUserId(1L)).willReturn(User.builder().email("admin@test.com").name("운영자")
                .provider(Provider.GOOGLE).providerId("google-id-1").role(role).build());
    }
}
