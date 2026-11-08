package com.siwol025.flight_monitor.booking.frontend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.resolver.AuthUserArgumentResolver;
import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.booking.controller.FlightOfferController;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.user.service.UserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 예매 화면 정적 파일: 인증 없이 열리고, 브라우저로 가는 파일에 Toss 시크릿 키가 없다 */
@WebMvcTest(FlightOfferController.class)
@Import(AuthUserArgumentResolver.class)
@MockitoBean(types = JpaMetamodelMappingContext.class)
class BookingStaticPagesTest {

    // test_sk_·live_sk_·sk_ 시크릿 키 형식
    private static final Pattern SECRET_KEY = Pattern.compile("(?<![A-Za-z0-9])(?:test_|live_)?sk_");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @ParameterizedTest
    @ValueSource(strings = {"index.html", "checkout.html", "payment.html", "payment-success.html",
            "payment-fail.html", "bookings.html", "app.js", "app.css"})
    void 정적_파일은_인증_없이_200으로_열린다(String file) throws Exception {
        mockMvc.perform(get("/" + file))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"index.html", "checkout.html", "payment.html", "payment-success.html",
            "payment-fail.html", "bookings.html", "app.js", "app.css"})
    void 정적_파일에_시크릿_키가_없다(String file) throws IOException {
        String content = new ClassPathResource("static/" + file).getContentAsString(StandardCharsets.UTF_8);

        assertThat(SECRET_KEY.matcher(content).find()).isFalse();
    }
}
