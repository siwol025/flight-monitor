package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.siwol025.flight_monitor.FlightMonitorApplication;
import com.siwol025.flight_monitor.support.ComposeMysql;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 스키마 마이그레이션(Flyway)을 compose와 같은 MySQL로 검사한다: 빈 DB에 기준선을 적용하고 엔티티 검증(ddl-auto=validate)으로
 * 기동한다.
 *
 * <p>MySQL은 docker-compose.yml의 mysql 정의(이미지·entrypoint·environment)를 그대로 읽어 GenericContainer로 띄운다
 * ({@link ComposeMysql}).</p>
 *
 * <p>앱 쪽은 영속 계층 자동 설정(DataSource·Flyway·Hibernate)만 기동하고, 설정은 메인 application.yml과
 * application-docker.yml을 파일로 읽어 실제 배포 값(URL 파라미터, ddl-auto=validate)을 쓴다.</p>
 */
@Testcontainers
class DatabaseSchemaMigrationTest {

    // 가짜 자격 증명(.env 값이 아니다). compose에서는 .env의 DB_USERNAME·DB_PASSWORD가 들어간다
    private static final String APP_USER = "flight_app";
    private static final String DB_PASSWORD = ComposeMysql.DB_PASSWORD;
    private static final String DATABASE = ComposeMysql.DATABASE;

    // 자격 증명 변형마다 컨테이너 하나(이미지 초기화 경로가 다르다). 테스트 사이에는 DATABASE만 다시 만든다
    @Container
    private static final GenericContainer<?> APP_USER_DB = ComposeMysql.container(APP_USER);
    // .env의 DB_USERNAME이 root인 경우: 이미지는 MYSQL_USER=root를 거부하므로 compose entrypoint가 root 비밀번호만 둔다
    @Container
    private static final GenericContainer<?> ROOT_USER_DB = ComposeMysql.container("root");

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
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS", "3:SUCCESS");
        });
    }

    @Test
    void 스키마_다시기동해도_이미적용된마이그레이션은건너뜀() {
        app(APP_USER_DB, APP_USER).run(context -> assertThat(context).hasNotFailed());

        // 이미 적용된 기준선은 이력으로 알아보고 다시 돌리지 않는다(다시 돌리면 CREATE TABLE이 "이미 있음"으로 실패한다)
        app(APP_USER_DB, APP_USER).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS", "3:SUCCESS");
        });
    }

    @Test
    void 스키마_두인스턴스동시기동_한번만적용() {
        // compose에서는 앱과 mock 인스턴스가 같은 DB로 함께 뜬다. Flyway의 MySQL 잠금으로 한쪽만 적용하고 다른 쪽은 기다렸다 건너뛴다.
        // 기준선 적용은 수백 ms라 다른 인스턴스의 기동보다 빨리 끝나 겹치지 않을 수 있다. 적용하는 쪽을 잠깐 붙잡아 반드시 겹치게 한다
        Runnable instance = () -> app(APP_USER_DB, APP_USER).withBean(SlowMigration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS", "3:SUCCESS");
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

    @Test
    void 기존행_TICKETED() {
        // 간소화 예매(V2)의 mock 즉시 주문 내역이 있는 DB에 예매 상태를 추가한다. V2까지는 지금 엔티티와 맞지 않아 검증을 끈다
        app(APP_USER_DB, APP_USER).withPropertyValues("spring.flyway.target=2", "spring.jpa.hibernate.ddl-auto=none")
                .run(context -> assertThat(context).hasNotFailed());
        execute(APP_USER_DB,
                "INSERT INTO users (id, email, provider, provider_id, user_role) VALUES (1, 'u@test.com', 'GOOGLE', 'g1', 'USER')",
                """
                INSERT INTO bookings (user_id, offer_id, provider_order_id, booking_reference, departure_airport,
                    arrival_airport, departure_date, seat_grade, carrier_code, carrier_name, flight_number, departing_at,
                    arriving_at, amount, currency, passenger_title, given_name, family_name, passenger_gender, born_on,
                    email, phone_number, created_at)
                VALUES (1, 'off_1', 'ord_1', '000001', 'ICN', 'NRT', '2026-11-26', 'ECONOMY', 'ZZ', 'Duffel Airways',
                    '1001', '2026-11-26 09:00:00', '2026-11-26 11:20:00', 250000.00, 'USD', 'MR', 'Gildong', 'Hong', 'M',
                    '1990-01-01', 'p@test.com', '+821012345678', NOW(6))
                """);

        app(APP_USER_DB, APP_USER).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(appliedMigrations(context)).containsExactly("1:SUCCESS", "2:SUCCESS", "3:SUCCESS");
        });

        assertThat(query(APP_USER_DB, "SELECT status FROM bookings")).containsExactly("TICKETED");
    }

    private static GenericContainer<?> database(String username) {
        return "root".equals(username) ? ROOT_USER_DB : APP_USER_DB;
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
