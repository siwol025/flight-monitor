package com.siwol025.flight_monitor.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(FlightOfferController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class FlightOfferControllerTest {

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
        User user = User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .provider(Provider.GOOGLE)
                .providerId("google-id-123")
                .role(Role.USER)
                .build();
        given(jwtProvider.getUserIdFromAccessToken(anyString())).willReturn(1L);
        given(userService.getByUserId(1L)).willReturn(user);
    }

    // 검증을 통과하는 검색. skip으로 준 파라미터 하나만 뺀다
    private static MockHttpServletRequestBuilder search(String skip) {
        MockHttpServletRequestBuilder request = get("/api/flights/offers");
        String[][] params = {{"departureAirport", "ICN"}, {"arrivalAirport", "NRT"},
                {"departureDate", "2026-11-26"}, {"seatGrade", "ECONOMY"}};
        for (String[] param : params) {
            if (!param[0].equals(skip)) {
                request.param(param[0], param[1]);
            }
        }
        return request;
    }

    @Test
    void searchOffers_인증없음_401() throws Exception {
        mockMvc.perform(search(""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchOffers_정상요청_200() throws Exception {
        given(bookingService.searchOffers(any(), any(), any(), any())).willReturn(List.of());

        mockMvc.perform(search("").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} 없음")
    @ValueSource(strings = {"departureAirport", "arrivalAirport", "departureDate", "seatGrade"})
    void searchOffers_파라미터누락_400(String missing) throws Exception {
        mockMvc.perform(search(missing).header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("INVALID_INPUT"));
        verify(bookingService, never()).searchOffers(any(), any(), any(), any());
    }
}
