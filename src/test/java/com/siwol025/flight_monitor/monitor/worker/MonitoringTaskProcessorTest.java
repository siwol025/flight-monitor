package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringTaskProcessorTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PriceMonitorService priceMonitorService;

    @InjectMocks
    private MonitoringTaskProcessor processor;

    @Test
    void processTask_정상JSON_priceMonitorService_호출됨() throws Exception {
        // given
        FlightMonitorTaskDto expectedDto = new FlightMonitorTaskDto(42L, SeatGrade.ECONOMY);
        String json = "{\"flightId\":42,\"seatGrade\":\"ECONOMY\"}";

        org.mockito.BDDMockito.given(objectMapper.readValue(json, FlightMonitorTaskDto.class))
                .willReturn(expectedDto);

        // when
        processor.processTask(json);

        // then
        ArgumentCaptor<FlightMonitorTaskDto> captor = ArgumentCaptor.forClass(FlightMonitorTaskDto.class);
        verify(priceMonitorService).checkPriceAndNotify(captor.capture());

        FlightMonitorTaskDto captured = captor.getValue();
        assertThat(captured.flightId()).isEqualTo(42L);
        assertThat(captured.seatGrade()).isEqualTo(SeatGrade.ECONOMY);
    }

    @Test
    void processTask_잘못된JSON_예외_삼키고_priceMonitorService_미호출() throws Exception {
        // given
        String invalidJson = "not-valid-json";

        org.mockito.BDDMockito.given(objectMapper.readValue(invalidJson, FlightMonitorTaskDto.class))
                .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "parse error"));

        // when & then
        assertDoesNotThrow(() -> processor.processTask(invalidJson));
        verify(priceMonitorService, never()).checkPriceAndNotify(any());
    }
}
