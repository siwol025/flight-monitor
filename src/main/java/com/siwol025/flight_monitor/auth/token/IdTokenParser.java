package com.siwol025.flight_monitor.auth.token;

import com.siwol025.flight_monitor.user.domain.Provider;

public interface IdTokenParser {
    Provider getProvider();

    String getProviderId(String idToken);

    String getEmail(String idToken);

    String getName(String idToken);
}
