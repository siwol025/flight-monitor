package com.siwol025.flight_monitor.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Processing;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.booking.service.CheckoutService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class BookingControllerTest {

    private static final String KEY = "6f1c2c1e-0d1b-4c1e-9a55-2b1f0f6a7c01";

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
    private CheckoutService checkoutService;

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
        mockMvc.perform(post("/api/bookings").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void book_정상요청_201() throws Exception {
        given(checkoutService.purchase(any(), eq(KEY), any()))
                .willReturn(new Fresh<>(ResponseEntity.status(201).body(new BookingStatusResponse(7L, BookingStatus.HELD))));

        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(7));
    }

    @Test
    void book_같은키저장응답_상태와본문그대로() throws Exception {
        given(checkoutService.purchase(any(), eq(KEY), any()))
                .willReturn(new Replay<>(202, "{\"bookingId\":7,\"status\":\"HOLD_REQUESTED\"}"));

        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("HOLD_REQUESTED"));
    }

    @Test
    void book_같은키처리중_409_RetryAfter() throws Exception {
        given(checkoutService.purchase(any(), eq(KEY), any())).willReturn(new Processing<>());

        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.tag").value("IDEMPOTENT_REQUEST_PROCESSING"));
    }

    @Test
    void book_키없음_400() throws Exception {
        mockMvc.perform(post("/api/bookings").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("IDEMPOTENCY_KEY_REQUIRED"));
        verify(checkoutService, never()).purchase(any(), any(), any());
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
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("INVALID_INPUT"));
        verify(checkoutService, never()).purchase(any(), any(), any());
    }

    @Test
    void getMyBookings_인증없음_401() throws Exception {
        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isUnauthorized());
    }
}
