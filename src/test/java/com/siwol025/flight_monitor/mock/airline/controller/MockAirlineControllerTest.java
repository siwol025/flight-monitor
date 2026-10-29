package com.siwol025.flight_monitor.mock.airline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.auth.token.JwtProvider;
import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airline.service.MockAirlineService;
import com.siwol025.flight_monitor.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * mock 항공사 등록 API가 JSON 본문을 받아 저장하고 필수값을 검증하는지, 실제 서비스·리포지토리(H2)로 검사한다.
 */
@WebMvcTest(MockAirlineController.class)
@AutoConfigureDataJpa
@AutoConfigureTestDatabase
@Import(MockAirlineService.class)
class MockAirlineControllerTest {

    // 웹 슬라이스에 함께 올라오는 로그인 사용자 리졸버의 의존성. mock API는 로그인을 쓰지 않는다
    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockAirlineRepository mockAirlineRepository;

    @Test
    void 항공사등록_JSON본문_저장됨() throws Exception {
        mockMvc.perform(post("/external/api/airlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZ\",\"name\":\"Verify Air\"}"))
                .andExpect(status().isOk());

        assertThat(mockAirlineRepository.findByAirlineCode("ZZ"))
                .get().extracting(MockAirline::getAirlineName).isEqualTo("Verify Air");
    }

    @Test
    void 항공사등록_필수값누락_400() throws Exception {
        long before = mockAirlineRepository.count();

        mockMvc.perform(post("/external/api/airlines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"Verify Air\"}"))
                .andExpect(status().isBadRequest());

        assertThat(mockAirlineRepository.count()).isEqualTo(before);
    }
}
