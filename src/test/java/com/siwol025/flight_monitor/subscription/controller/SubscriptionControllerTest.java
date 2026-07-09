package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import io.jsonwebtoken.Claims;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .provider(Provider.GOOGLE)
                .providerId("google-id-123")
                .role(Role.USER)
                .build();

        Claims claims = mock(Claims.class);
        given(claims.get("userId", Long.class)).willReturn(1L);

        given(jwtProvider.parseToken(anyString())).willReturn(claims);
        given(userService.getByUserId(anyLong())).willReturn(mockUser);
    }

    @Test
    void subscribe_정상흐름_204_반환() throws Exception {
        doNothing().when(subscriptionService).subscribe(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void subscribe_요청바디_없으면_400_반환() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMySubscriptions_정상흐름_200_반환() throws Exception {
        given(subscriptionService.getSubscriptions(any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/subscriptions/my")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelSubscription_비인증_401_반환() throws Exception {
        mockMvc.perform(delete("/api/subscriptions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMySubscriptions_정상흐름_응답바디_구조_검증() throws Exception {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .subscriptionId(42L)
                .flightNumber("KE123")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .departureTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .seatGrade(SeatGrade.ECONOMY)
                .subscribedPrice(new BigDecimal("150000"))
                .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build();

        given(subscriptionService.getSubscriptions(any())).willReturn(List.of(response));

        mockMvc.perform(get("/api/subscriptions/my")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subscriptionId").value(42))
                .andExpect(jsonPath("$[0].flightNumber").value("KE123"))
                .andExpect(jsonPath("$[0].airlineCode").value("KE"))
                .andExpect(jsonPath("$[0].departureAirportCode").value("ICN"))
                .andExpect(jsonPath("$[0].arrivalAirportCode").value("NRT"))
                .andExpect(jsonPath("$[0].seatGrade").value("ECONOMY"))
                .andExpect(jsonPath("$[0].subscribedPrice").value(150000));
    }

    @Test
    void subscribe_정상흐름_응답바디_subscriptionId_포함_검증() throws Exception {
        doNothing().when(subscriptionService).subscribe(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\"}"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void request_targetPrice_지정시_서비스에_전달된다() throws Exception {
        doNothing().when(subscriptionService).subscribe(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\", \"targetPrice\": 120000}"))
                .andExpect(status().isNoContent());

        verify(subscriptionService).subscribe(any(), any(), any(), eq(new BigDecimal("120000")), any());
    }

    @Test
    void request_dropThresholdPercent_지정시_서비스에_전달된다() throws Exception {
        doNothing().when(subscriptionService).subscribe(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\", \"dropThresholdPercent\": 10.00}"))
                .andExpect(status().isNoContent());

        verify(subscriptionService).subscribe(any(), any(), any(), any(), eq(new BigDecimal("10.00")));
    }

    @Test
    void request_targetPrice_음수이면_400_반환() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\", \"targetPrice\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void request_dropThresholdPercent_100초과이면_400_반환() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightId\": 1, \"seatGrade\": \"ECONOMY\", \"dropThresholdPercent\": 101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSubscription_조건없음_null_응답됨() throws Exception {
        SubscriptionDetailResponse response = SubscriptionDetailResponse.builder()
                .subscriptionId(10L)
                .flightNumber("KE999")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .departureTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .arrivalTime(LocalDateTime.of(2026, 8, 1, 12, 0))
                .seatGrade(SeatGrade.ECONOMY)
                .subscribedPrice(new BigDecimal("200000"))
                .currentPrice(new BigDecimal("200000"))
                .priceDifference(BigDecimal.ZERO)
                .targetPrice(null)
                .dropThresholdPercent(null)
                .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build();

        given(subscriptionService.getSubscription(any(), eq(10L))).willReturn(response);

        mockMvc.perform(get("/api/subscriptions/10")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPrice").value(nullValue()))
                .andExpect(jsonPath("$.dropThresholdPercent").value(nullValue()));
    }

    @Test
    void getSubscriptions_조건지정_응답DTO에_포함됨() throws Exception {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .subscriptionId(1L)
                .flightNumber("KE123")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .departureTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .seatGrade(SeatGrade.ECONOMY)
                .subscribedPrice(new BigDecimal("150000"))
                .targetPrice(new BigDecimal("120000"))
                .dropThresholdPercent(new BigDecimal("10.00"))
                .createdAt(LocalDateTime.of(2026, 7, 1, 12, 0))
                .build();

        given(subscriptionService.getSubscriptions(any())).willReturn(List.of(response));

        mockMvc.perform(get("/api/subscriptions/my")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetPrice").value(120000))
                .andExpect(jsonPath("$[0].dropThresholdPercent").value(10.00));
    }
}
