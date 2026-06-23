package com.siwol025.flight_monitor.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.global.exception.custom.UnauthorizedException;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.request.SubscriptionRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException(ErrorTag.UNAUTHORIZED);
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException(ErrorTag.DUPLICATE_SUBSCRIPTION);
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND);
        }

        @GetMapping("/server-error")
        void serverError() {
            throw new RuntimeException("Unexpected internal error");
        }

        @PostMapping("/valid")
        void requireValid(@Valid @RequestBody SubscriptionRequest request) {}
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void UnauthorizedException이면_401과_ErrorResponse반환() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.tag").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(ErrorTag.UNAUTHORIZED.getMessage()));
    }

    @Test
    void BadRequestException이면_400과_ErrorResponse반환() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("DUPLICATE_SUBSCRIPTION"))
                .andExpect(jsonPath("$.message").value(ErrorTag.DUPLICATE_SUBSCRIPTION.getMessage()));
    }

    @Test
    void NotFoundException이면_404와_ErrorResponse반환() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.tag").value("FLIGHT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorTag.FLIGHT_NOT_FOUND.getMessage()));
    }

    @Test
    void 미핸들예외이면_500과_안전한_메시지반환() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.tag").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    void Valid실패이면_400과_INVALID_INPUT반환() throws Exception {
        // flightId, seatGrade 둘 다 @NotNull인데 빈 JSON 전송
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tag").value("INVALID_INPUT"));
    }

    @Test
    void 응답에_스택트레이스_미노출() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                // 내부 예외 메시지(Unexpected internal error)가 응답에 노출되지 않아야 함
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }
}
