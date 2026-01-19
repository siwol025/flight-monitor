package com.siwol025.flight_monitor.auth.dto.response;

import lombok.Builder;

@Builder
public record TokenResponse(String accessToken, String refreshToken) {
}
