package com.siwol025.flight_monitor.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

/**
 * docker-compose.yml의 mysql 정의(이미지·entrypoint·environment)를 그대로 띄우는 테스트 MySQL.
 *
 * <p>Testcontainers의 MySQLContainer를 쓰지 않는 이유: 준비 확인 때 같은 사용자로 먼저 로그인(allowPublicKeyRetrieval)해
 * 서버에 비밀번호가 캐시되므로, 앱의 첫 로그인이 실패하는 설정(useSSL=false → "Public Key Retrieval is not allowed")도
 * 통과해 버린다(실측). compose에는 그런 선 로그인이 없다.</p>
 */
public final class ComposeMysql {

    // 가짜 자격 증명(.env 값이 아니다). compose에서는 .env의 DB_USERNAME·DB_PASSWORD가 들어간다
    public static final String DB_PASSWORD = "test-only-password";
    public static final String DATABASE = "flight_monitor";

    // 조건부 UPDATE·유니크·경합 테스트가 함께 쓰는 root 사용자 MySQL. 처음 쓸 때 한 번 띄운다(정리는 Testcontainers가 한다)
    private static GenericContainer<?> shared;

    private ComposeMysql() {
    }

    /**
     * compose의 mysql 서비스 정의 그대로 띄울 MySQL. compose가 프로젝트의 .env로 하는 보간(${DB_USERNAME} 등)을
     * 테스트의 가짜 자격 증명으로 한다. 정의에 없는 값은 넣지 않는다(컨테이너가 받는 환경은 compose와 같다)
     */
    @SuppressWarnings("unchecked")
    public static GenericContainer<?> container(String username) {
        Map<String, String> dotEnv = Map.of("DB_USERNAME", username, "DB_PASSWORD", DB_PASSWORD);
        Map<String, Object> service = composeService("mysql");
        String[] entrypoint = ((List<String>) service.get("entrypoint")).stream()
                .map(part -> interpolate(part, dotEnv))
                .toArray(String[]::new);
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse((String) service.get("image")))
                .withExposedPorts(3306)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(entrypoint))
                // 초기화용 임시 서버(port: 0)가 아니라 실제 서버가 뜬 뒤
                .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        ((Map<String, Object>) service.get("environment"))
                .forEach((key, value) -> container.withEnv(key, interpolate(String.valueOf(value), dotEnv)));
        return container;
    }

    /** 앱의 영속 계층을 공유 MySQL에 붙인다: Flyway로 스키마를 만들고 엔티티를 검증(validate)한다 */
    public static synchronized void registerSharedDataSource(DynamicPropertyRegistry registry) {
        if (shared == null) {
            shared = container("root");
            shared.start();
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + shared.getHost() + ":"
                + shared.getMappedPort(3306) + "/" + DATABASE + "?serverTimezone=UTC&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    /** compose 보간: ${NAME}·${NAME:?…} 등은 .env 값으로, $$는 문자 $(컨테이너 셸이 펼친다) */
    private static String interpolate(String value, Map<String, String> dotEnv) {
        return Pattern.compile("\\$\\$|\\$\\{(\\w+)[^}]*}").matcher(value).replaceAll(match -> {
            if (match.group().equals("$$")) {
                return Matcher.quoteReplacement("$");
            }
            String resolved = dotEnv.get(match.group(1));
            if (resolved == null) {
                throw new IllegalStateException("테스트가 모르는 compose 변수: " + match.group(1));
            }
            return Matcher.quoteReplacement(resolved);
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> composeService(String name) {
        try (InputStream in = Files.newInputStream(Path.of("docker-compose.yml"))) {
            Map<String, Object> compose = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
