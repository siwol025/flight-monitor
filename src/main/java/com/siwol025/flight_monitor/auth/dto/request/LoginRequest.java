package com.siwol025.flight_monitor.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String idToken) {
}
