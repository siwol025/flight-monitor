package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.siwol025.flight_monitor.FlightMonitorApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

/**
 * 스키마 마이그레이션(Flyway)을 compose와 같은 MySQL로 검사한다: 빈 DB에 기준선을 적용하고 엔티티 검증(ddl-auto=validate)으로
 * 기동한다.
 *
 * <p>MySQL은 docker-compose.yml의 mysql 정의(이미지·entrypoint·environment)를 그대로 읽어 GenericContainer로 띄운다.
 * Testcontainers의 MySQLContainer를 쓰지 않는 이유: 준비 확인 때 같은 사용자로 먼저 로그인(allowPublicKeyRetrieval)해
 * 서버에 비밀번호가 캐시되므로, 앱의 첫 로그인이 실패하는 설정(useSSL=false → "Public Key Retrieval is not allowed")도
 * 통과해 버린다(실측). compose에는 그런 선 로그인이 없다.</p>
 *
 * <p>앱 쪽은 영속 계층 자동 설정(DataSource·Flyway·Hibernate)만 기동하고, 설정은 메인 application.yml과
 * application-docker.yml을 파일로 읽어 실제 배포 값(URL 파라미터, ddl-auto=validate)을 쓴다.</p>
 */
@Testcontainers
class DatabaseSchemaMigrationTest {

    // 가짜 자격 증명(.env 값이 아니다). compose에서는 .env의 DB_USERNAME·DB_PASSWORD가 들어간다
    private static final String APP_USER = "flight_app";
    private static final String DB_PASSWORD = "test-only-password";
    private static final String DATABASE = "flight_monitor";

    // 자격 증명 변형마다 컨테이너 하나(이미지 초기화 경로가 다르다). 테스트 사이에는 DATABASE만 다시 만든다
    @Container
    private static final GenericContainer<?> APP_USER_DB = composeMysql(APP_USER);
    // .env의 DB_USERNAME이 root인 경우: 이미지는 MYSQL_USER=root를 거부하므로 compose entrypoint가 root 비밀번호만 둔다
    @Container
    private static final GenericContainer<?> ROOT_USER_DB = composeMysql("root");

    @BeforeEach
    void emptyDatabase() {
        recreateDatabase(APP_USER_DB);
        recreateDatabase(ROOT_USER_DB);
    }

    @ParameterizedTest(name = "DB_USERNAME={0}")
    @ValueSource(strings = {APP_USER, "root"})
    void 스키마_빈DB에Flyway적용후_엔티티검증통과(String username) {
        app(database(username), username).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS");
        });
    }

    @Test
    void 스키마_다시기동해도_이미적용된마이그레이션은건너뜀() {
        app(APP_USER_DB, APP_USER).run(context -> assertThat(context).hasNotFailed());

        // 이미 적용된 기준선은 이력으로 알아보고 다시 돌리지 않는다(다시 돌리면 CREATE TABLE이 "이미 있음"으로 실패한다)
        app(APP_USER_DB, APP_USER).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS");
        });
    }

    @Test
    void 스키마_두인스턴스동시기동_한번만적용() {
        // compose에서는 앱과 mock 인스턴스가 같은 DB로 함께 뜬다. Flyway의 MySQL 잠금으로 한쪽만 적용하고 다른 쪽은 기다렸다 건너뛴다.
        // 기준선 적용은 수백 ms라 다른 인스턴스의 기동보다 빨리 끝나 겹치지 않을 수 있다. 적용하는 쪽을 잠깐 붙잡아 반드시 겹치게 한다
        Runnable instance = () -> app(APP_USER_DB, APP_USER).withBean(SlowMigration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS");
        });

        CompletableFuture<Void> app = CompletableFuture.runAsync(instance);
        CompletableFuture<Void> mock = CompletableFuture.runAsync(instance);

        assertThatCode(() -> CompletableFuture.allOf(app, mock).join()).doesNotThrowAnyException();
    }

    @Test
    void 스키마_기존테이블있고이력없는DB_기동실패_아무것도안바꿈() {
        // 옛 호스트 DB 상황: ddl-auto로 만든 테이블과 데이터는 있지만 Flyway 이력은 없다(컷오버 전 dev가 여기를 가리킬 수 있다)
        execute(APP_USER_DB, "CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT, email VARCHAR(255), PRIMARY KEY (id))",
                "INSERT INTO users (email) VALUES ('kept@test.com')");

        app(APP_USER_DB, APP_USER).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("no schema history table");
        });

        // 기준선을 덮어 적용하거나 이력 테이블을 만들지 않는다(baselineOnMigrate 꺼 둠)
        assertThat(query(APP_USER_DB, "SHOW TABLES")).containsExactly("users");
        assertThat(query(APP_USER_DB, "SELECT email FROM users")).containsExactly("kept@test.com");
    }

    private static GenericContainer<?> database(String username) {
        return "root".equals(username) ? ROOT_USER_DB : APP_USER_DB;
    }

    /**
     * compose의 mysql 서비스 정의 그대로 띄운 MySQL. compose가 프로젝트의 .env로 하는 보간(${DB_USERNAME} 등)을
     * 테스트의 가짜 자격 증명으로 한다. 정의에 없는 값은 넣지 않는다(컨테이너가 받는 환경은 compose와 같다)
     */
    @SuppressWarnings("unchecked")
    private static GenericContainer<?> composeMysql(String username) {
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

    /** 앱의 영속 계층(DataSource → Flyway → Hibernate validate)을 docker 프로필 설정으로 이 DB에 붙여 기동한다 */
    private static ApplicationContextRunner app(GenericContainer<?> db, String username) {
        String dockerUrl = (String) yml("src/main/resources/application-docker.yml").getProperty("spring.datasource.url");
        String url = dockerUrl.replace("//mysql:3306/", "//" + db.getHost() + ":" + db.getMappedPort(3306) + "/");
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.addLast(yml("src/main/resources/application-docker.yml"));
                    sources.addLast(yml("src/main/resources/application.yml"));
                })
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withUserConfiguration(Entities.class)
                .withPropertyValues("spring.datasource.url=" + url, "spring.datasource.username=" + username,
                        "spring.datasource.password=" + DB_PASSWORD);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = FlightMonitorApplication.class)
    static class Entities {
    }

    /** 마이그레이션을 실제로 적용하는 인스턴스(잠금을 쥔 쪽)만 잠깐 멈춘다. 그동안 다른 인스턴스는 잠금을 기다려야 한다 */
    static class SlowMigration implements Callback {

        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.BEFORE_EACH_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(Event event, Context context) {
            return true;
        }

        @Override
        public void handle(Event event, Context context) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String getCallbackName() {
            return "slow-migration";
        }
    }

    private static List<String> appliedMigrations(ApplicationContext context) {
        MigrationInfo[] applied = context.getBean(Flyway.class).info().applied();
        return Arrays.stream(applied).map(m -> m.getVersion() + ":" + m.getState()).toList();
    }

    private static PropertySource<?> yml(String path) {
        try {
            return new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 테스트마다 빈 DB. root로 지우고 다시 만든다(일반 사용자의 DB 권한은 이름에 걸려 있어 그대로 남는다) */
    private static void recreateDatabase(GenericContainer<?> db) {
        try (Connection connection = DriverManager.getConnection(rootUrl(db, ""), "root", DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void execute(GenericContainer<?> db, String... sqls) {
        try (Connection connection = DriverManager.getConnection(rootUrl(db, DATABASE), "root", DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            for (String sql : sqls) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> query(GenericContainer<?> db, String sql) {
        try (Connection connection = DriverManager.getConnection(rootUrl(db, DATABASE), "root", DB_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // 테스트 준비용 root 연결(앱의 일반 사용자 연결과 따로 두어 그 사용자의 첫 로그인을 미리 하지 않는다)
    private static String rootUrl(GenericContainer<?> db, String database) {
        return "jdbc:mysql://" + db.getHost() + ":" + db.getMappedPort(3306) + "/" + database + "?serverTimezone=UTC";
    }
}
