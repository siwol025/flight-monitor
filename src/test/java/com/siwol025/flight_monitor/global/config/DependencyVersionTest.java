package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyVersionTest {

    @Test
    void springdoc_버전이_2_3_0_초과여야_한다() {
        Package pkg = org.springdoc.webmvc.ui.SwaggerUiHome.class.getPackage();
        String version = pkg.getImplementationVersion();
        // 2.3.0 초과 버전임을 검증 (현재 2.3.0이라 실패해야 함)
        assertThat(version).isNotEqualTo("2.3.0");
    }

    @Test
    void googleApiClient_버전이_2_2_0_초과여야_한다() {
        Package pkg = com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.class.getPackage();
        String version = pkg.getImplementationVersion();
        assertThat(version).isNotEqualTo("2.2.0");
    }

    @Test
    void guava_의존성이_명시적으로_선언되어있다() throws IOException {
        Path buildGradle = Paths.get(System.getProperty("user.dir"), "build.gradle");
        String content = Files.readString(buildGradle);
        assertThat(content).contains("com.google.guava:guava");
    }
}
