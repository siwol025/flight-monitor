package com.siwol025.flight_monitor.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 패키지 경계 규칙. 운영 클래스만 검사한다(테스트 클래스 제외).
 */
class ArchitectureRulesTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.siwol025.flight_monitor");

    @Test
    void duffel어댑터타입은_어댑터패키지밖에서_참조되지않음() {
        // 빈 조립 설정(DuffelHttpClientConfig)도 어댑터 패키지 안에 있으므로 예외가 필요 없다
        noClasses().that().resideOutsideOfPackage("..fare.adapter.duffel..")
                .should().dependOnClassesThat().resideInAPackage("..fare.adapter.duffel..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    @Disabled("노선 전환으로 도메인의 mock DTO(MockFlightResponse) 의존이 사라지면 활성화")
    void mock밖에서_mock패키지를_import하지않음() {
        noClasses().that().resideOutsideOfPackage("com.siwol025.flight_monitor.mock..")
                .should().dependOnClassesThat().resideInAPackage("com.siwol025.flight_monitor.mock..")
                .check(PRODUCTION_CLASSES);
    }
}
