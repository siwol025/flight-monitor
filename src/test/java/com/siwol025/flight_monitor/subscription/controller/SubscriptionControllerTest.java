package com.siwol025.flight_monitor.subscription.controller;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        doNothing().when(subscriptionService).subscribe(any(), any(), any());

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
}
