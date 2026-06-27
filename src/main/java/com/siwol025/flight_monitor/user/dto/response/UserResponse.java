package com.siwol025.flight_monitor.user.dto.response;

import com.siwol025.flight_monitor.user.domain.User;

public record UserResponse(String email, String name) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getEmail(), user.getName());
    }
}
