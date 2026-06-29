package com.siwol025.flight_monitor.global.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorTagTest {

    @Test
    void ErrorTag에_INVALID_INPUT이_존재한다() {
        assertThat(ErrorTag.INVALID_INPUT).isNotNull();
    }
}
