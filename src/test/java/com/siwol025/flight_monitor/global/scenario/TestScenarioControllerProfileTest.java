package com.siwol025.flight_monitor.global.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class TestScenarioControllerProfileTest {

    @Test
    void findOrCreateAirline_헬퍼_메서드가_DataSetupService에_존재한다() throws NoSuchMethodException {
        java.lang.reflect.Method method = DataSetupService.class
                .getDeclaredMethod("findOrCreateAirline", String.class, String.class);
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("MockAirline");
    }

    @Test
    void findOrCreateAirport_헬퍼_메서드가_DataSetupService에_존재한다() throws NoSuchMethodException {
        java.lang.reflect.Method method = DataSetupService.class
                .getDeclaredMethod("findOrCreateAirport", String.class, String.class);
        assertThat(method.getReturnType().getSimpleName()).isEqualTo("MockAirport");
    }

    @Test
    void TestScenarioController_운영_프로파일에서_빈_등록_안됨() {
        Profile profile = TestScenarioController.class.getAnnotation(Profile.class);

        assertThat(profile)
                .as("TestScenarioController에 @Profile 어노테이션이 없습니다. prod/docker 환경에서 파괴적 엔드포인트가 노출됩니다.")
                .isNotNull();

        assertThat(Arrays.asList(profile.value()))
                .as("@Profile 목록에 'prod' 또는 'docker'가 포함되어서는 안 됩니다.")
                .doesNotContain("prod", "docker");
    }
}
