-- 노선 기반 구독 전환: 구독 대상을 항공편에서 노선(출발 공항, 도착 공항, 출발일, 좌석등급)으로 바꾼다
-- 적용 순서: DB 변경 먼저 실행 → 서버 배포 (ddl-auto: validate 환경)
-- 되돌릴 수 없음: 기존 구독·항공편·가격 행을 모두 지운다(users는 유지). 적용 전에 백업한다.
-- Redis 옛 키(monitoring:task:queue, flight:price:*) 삭제와 Kafka 토픽 재생성은 이 파일 밖의 컷오버 작업이다.
--
-- [적용 전 확인] 아래 조회에 예상 밖의 행이 나오면 적용하지 말고 그 FK부터 정리한다.
-- 이 파일은 엔티티 기준 스키마로 검증했으므로, 엔티티 밖의 테이블이 지울 테이블을 참조하면 잡지 못한다.
--
--   SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME
--   FROM information_schema.KEY_COLUMN_USAGE
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND REFERENCED_TABLE_NAME IN ('subscriptions', 'flight_seat_grade_prices', 'flights', 'routes', 'route_latest_fares');
--
-- 예상 결과: subscriptions.flight_id → flights, flight_seat_grade_prices.flight_id → flights
-- (노선 테이블이 이미 있는 DB라면 route_latest_fares.route_id → routes도 나온다)

-- 1. 옛 테이블 제거
-- subscriptions는 Hibernate가 만든 FK 이름이 환경마다 달라 ALTER 대신 지우고 새로 만든다(행은 어차피 모두 지운다)
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS flight_seat_grade_prices;
DROP TABLE IF EXISTS flights;
-- 노선 테이블을 이미 만든 DB(노선 엔티티가 들어간 버전을 ddl-auto: create로 띄운 적이 있는 경우)도 같은 정의로 맞춘다
DROP TABLE IF EXISTS route_latest_fares;
DROP TABLE IF EXISTS routes;

-- 2. 노선: 같은 노선의 구독자는 한 행을 공유한다. 업체가 노선을 거부하면(422) INVALID로 바꿔 감시에서 뺀다
CREATE TABLE routes
(
    id                BIGINT                               NOT NULL AUTO_INCREMENT,
    departure_airport VARCHAR(3)                           NOT NULL,
    arrival_airport   VARCHAR(3)                           NOT NULL,
    departure_date    DATE                                 NOT NULL,
    seat_grade        ENUM ('BUSINESS', 'ECONOMY', 'FIRST') NOT NULL,
    status            ENUM ('ACTIVE', 'INVALID')           NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_routes_key UNIQUE (departure_airport, arrival_airport, departure_date, seat_grade)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 3. 노선별 최신 최저가(노선당 1행): 이전 가격의 단일 저장소. observed_at은 마지막으로 확인한 시각(UTC 기준 시점)
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

-- 4. 구독: 노선 FK. 기준가(price)는 노선의 첫 최저가를 관측하기 전(측정 대기)에는 비어 있다
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

-- 5. 감시 작업 페이로드가 노선 ID 숫자 문자열(JSON 숫자)로 바뀌었다. 옛 JSON 객체 페이로드는 처리기가 읽지 못하므로 지운다
DELETE FROM fallback_monitoring_tasks WHERE JSON_TYPE(payload) = 'OBJECT';
