package com.siwol025.flight_monitor.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtVersionTest {

    @Test
    void jjwt_버전이_0_12_6이어야_한다() {
        Package jwtsPackage = io.jsonwebtoken.Jwts.class.getPackage();
        String version = jwtsPackage.getImplementationVersion();
        assertThat(version).isEqualTo("0.12.6");
    }
}
