package com.siwol025.flight_monitor.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.service.UserService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class BookingControllerTest {

    // 검증을 통과하는 구매 요청. 각 테스트는 필드 하나만 바꿔 400을 확인한다
    private static final String VALID_BODY = """
            {"offerId":"off_mock_1","amount":250000.00,"currency":"USD",
            "passenger":{"title":"mr","givenName":"Gildong","familyName":"Hong","gender":"m",
            "bornOn":"1990-01-01","email":"gildong@test.com","phoneNumber":"+821012345678"}}""";

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

    @Test
    void book_인증없음_401() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void book_정상요청_201() throws Exception {
        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    // 정상 요청에서 한 군데만 바꾼다
    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("offerId 빈 값", VALID_BODY.replace("\"off_mock_1\"", "\"\"")),
                Arguments.of("amount 0", VALID_BODY.replace("250000.00", "0")),
                Arguments.of("amount 음수", VALID_BODY.replace("250000.00", "-1")),
                Arguments.of("currency 2자", VALID_BODY.replace("\"USD\"", "\"US\"")),
                Arguments.of("currency 소문자", VALID_BODY.replace("\"USD\"", "\"usd\"")),
                Arguments.of("passenger 없음", VALID_BODY.replaceAll(",\\s*\"passenger\":\\{[^}]*}", "")),
                Arguments.of("title 허용값 밖", VALID_BODY.replace("\"mr\"", "\"sir\"")),
                Arguments.of("gender 허용값 밖", VALID_BODY.replace("\"gender\":\"m\"", "\"gender\":\"x\"")),
                Arguments.of("givenName 빈 값", VALID_BODY.replace("\"Gildong\"", "\" \"")),
                Arguments.of("familyName 빈 값", VALID_BODY.replace("\"Hong\"", "\"\"")),
                Arguments.of("bornOn 미래", VALID_BODY.replace("1990-01-01", "2999-01-01")),
                Arguments.of("bornOn 형식 오류", VALID_BODY.replace("1990-01-01", "1990-13-40")),
                Arguments.of("email 형식", VALID_BODY.replace("gildong@test.com", "not-an-email")),
                Arguments.of("phoneNumber E.164 아님", VALID_BODY.replace("+821012345678", "01012345678")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void book_요청검증실패_400(String description, String body) throws Exception {
        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("INVALID_INPUT"));
        verify(bookingService, never()).book(any(), any());
    }

    @Test
    void getMyBookings_인증없음_401() throws Exception {
        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isUnauthorized());
    }
}
