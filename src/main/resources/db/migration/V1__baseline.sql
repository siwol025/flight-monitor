-- 기준선: 현재 엔티티 전체(12개 테이블)의 스키마. 빈 DB에 Flyway가 앱 기동 때 처음 적용한다.
-- 만든 방법: 엔티티를 일회용 mysql:8.4.10에 ddl-auto: create로 띄워 얻은 SHOW CREATE TABLE을 바탕으로, 제약 이름을 읽을 수
-- 있게 바꾸고 FK가 참조하는 테이블부터 배치했다(열의 타입·NULL 여부는 그대로). 엔티티와 어긋나면 DatabaseSchemaMigrationTest가
-- 기동 실패(ddl-auto: validate)로 잡는다.
-- 적용된 파일은 고치지 않는다(Flyway 체크섬 검사로 기동이 실패한다). 스키마를 바꾸려면 이 폴더에 다음 버전 파일을 더한다.
-- docs/sql/의 V2·V3는 옛 호스트 DB를 사람이 직접 올리던 기록이라 이 폴더의 버전 번호와 무관하다.

-- 사용자·인증
CREATE TABLE users
(
    id          BIGINT                          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)                    NOT NULL,
    name        VARCHAR(255)                    NULL,
    provider    ENUM ('GOOGLE')                 NOT NULL,
    provider_id VARCHAR(255)                    NOT NULL,
    user_role   ENUM ('ADMIN', 'GUEST', 'USER') NOT NULL,
    created_at  DATETIME(6)                     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refresh_token
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    issued_at  DATETIME(6)  NOT NULL,
    expired_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 노선: 같은 노선의 구독자는 한 행을 공유한다. 업체가 노선을 거부하면(422) INVALID로 바꿔 감시에서 뺀다
CREATE TABLE routes
(
    id                BIGINT                                NOT NULL AUTO_INCREMENT,
    departure_airport VARCHAR(3)                            NOT NULL,
    arrival_airport   VARCHAR(3)                            NOT NULL,
    departure_date    DATE                                  NOT NULL,
    seat_grade        ENUM ('BUSINESS', 'ECONOMY', 'FIRST') NOT NULL,
    status            ENUM ('ACTIVE', 'INVALID')            NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_routes_key UNIQUE (departure_airport, arrival_airport, departure_date, seat_grade)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 노선별 최신 최저가(노선당 1행): 이전 가격의 단일 저장소. observed_at은 마지막으로 확인한 시각(UTC 기준 시점)
CREATE TABLE route_latest_fares
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    route_id      BIGINT         NOT NULL,
    amount        DECIMAL(12, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    carrier_code  VARCHAR(10)    NOT NULL,
    carrier_name  VARCHAR(255)   NOT NULL,
    flight_number VARCHAR(20)    NOT NULL,
    departing_at  DATETIME(6)    NOT NULL COMMENT '업체가 준 현지 시각',
    arriving_at   DATETIME(6)    NOT NULL COMMENT '업체가 준 현지 시각',
    observed_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_route_latest_fares_route UNIQUE (route_id),
    CONSTRAINT fk_route_latest_fares_route FOREIGN KEY (route_id) REFERENCES routes (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 구독: 노선 FK. 기준가(price)는 노선의 첫 최저가를 관측하기 전(측정 대기)에는 비어 있다
CREATE TABLE subscriptions
(
    id                     BIGINT                                 NOT NULL AUTO_INCREMENT,
    user_id                BIGINT                                 NULL,
    route_id               BIGINT                                 NOT NULL,
    price                  DECIMAL(12, 2)                         NULL COMMENT '구독 시점의 기준 가격 (측정 대기 동안 null)',
    status                 ENUM ('ACTIVE', 'EXPIRED', 'INACTIVE') NOT NULL,
    target_price           DECIMAL(12, 2)                         NULL COMMENT '목표 가격 (null이면 조건 없음, 노선 최저가의 통화 기준)',
    drop_threshold_percent DECIMAL(5, 2)                          NULL COMMENT '하락률 임계값 % (null이면 조건 없음)',
    created_at             DATETIME(6)                            NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_subscriptions_route FOREIGN KEY (route_id) REFERENCES routes (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 알림 이력(테스트용 도메인)
CREATE TABLE notification_history
(
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    to_email VARCHAR(255) NULL,
    subject  VARCHAR(255) NULL,
    content  VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Redis 장애 때 감시 작업을 담는 DB 폴백 큐. payload는 노선 ID 숫자 문자열(JSON 숫자)
CREATE TABLE fallback_monitoring_tasks
(
    id         BIGINT                        NOT NULL AUTO_INCREMENT,
    payload    JSON                          NOT NULL,
    status     ENUM ('PENDING', 'PROCESSED') NOT NULL,
    created_at DATETIME(6)                   NULL,
    updated_at DATETIME(6)                   NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- mock 외부 API(같은 코드의 mock 인스턴스가 Duffel 형식으로 응답할 때 쓰는 데이터)
CREATE TABLE mock_airlines
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    airline_code VARCHAR(10)  NOT NULL,
    airline_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mock_airlines_airline_code UNIQUE (airline_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_airports
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    airport_code VARCHAR(255) NOT NULL,
    airport_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_flights
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    flight_number        VARCHAR(255) NOT NULL,
    airline_id           BIGINT       NULL,
    departure_airport_id BIGINT       NULL,
    arrival_airport_id   BIGINT       NULL,
    departure_time       DATETIME(6)  NULL,
    arrival_time         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_mock_flights_airline FOREIGN KEY (airline_id) REFERENCES mock_airlines (id),
    CONSTRAINT fk_mock_flights_departure_airport FOREIGN KEY (departure_airport_id) REFERENCES mock_airports (id),
    CONSTRAINT fk_mock_flights_arrival_airport FOREIGN KEY (arrival_airport_id) REFERENCES mock_airports (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_flight_seat_prices
(
    id         BIGINT                                NOT NULL AUTO_INCREMENT,
    flight_id  BIGINT                                NULL,
    grade_name ENUM ('BUSINESS', 'ECONOMY', 'FIRST') NOT NULL,
    price      DECIMAL(12, 0)                        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_mock_flight_seat_prices_flight FOREIGN KEY (flight_id) REFERENCES mock_flights (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE mock_seats
(
    id          BIGINT                                NOT NULL AUTO_INCREMENT,
    flight_id   BIGINT                                NOT NULL,
    seat_number VARCHAR(10)                           NOT NULL,
    grade_name  ENUM ('BUSINESS', 'ECONOMY', 'FIRST') NOT NULL,
    is_booked   BIT(1)                                NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_mock_seats_flight FOREIGN KEY (flight_id) REFERENCES mock_flights (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
