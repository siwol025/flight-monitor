-- 간소화 예매의 구매 내역. 노선·항공편 값은 구매 직전에 재조회한 offer, 주문 ID·예약 번호는 업체가 확정한 주문에서 온다.
-- 승객은 Duffel 주문 생성의 passengers[] 필드를 따른다(성인 1명, 로그인 사용자와 별개).
CREATE TABLE bookings
(
    id                BIGINT                                     NOT NULL AUTO_INCREMENT,
    user_id           BIGINT                                     NOT NULL,
    offer_id          VARCHAR(100)                               NOT NULL,
    provider_order_id VARCHAR(100)                               NOT NULL,
    booking_reference VARCHAR(20)                                NOT NULL,
    departure_airport VARCHAR(3)                                 NOT NULL,
    arrival_airport   VARCHAR(3)                                 NOT NULL,
    departure_date    DATE                                       NOT NULL,
    seat_grade        ENUM ('BUSINESS', 'ECONOMY', 'FIRST')      NOT NULL,
    carrier_code      VARCHAR(10)                                NOT NULL,
    carrier_name      VARCHAR(255)                               NOT NULL,
    flight_number     VARCHAR(20)                                NOT NULL,
    departing_at      DATETIME(6)                                NOT NULL COMMENT '업체가 준 현지 시각',
    arriving_at       DATETIME(6)                                NOT NULL COMMENT '업체가 준 현지 시각',
    amount            DECIMAL(12, 2)                             NOT NULL,
    currency          VARCHAR(3)                                 NOT NULL,
    passenger_title   ENUM ('DR', 'MISS', 'MR', 'MRS', 'MS')     NOT NULL,
    given_name        VARCHAR(100)                               NOT NULL,
    family_name       VARCHAR(100)                               NOT NULL,
    passenger_gender  ENUM ('F', 'M')                            NOT NULL,
    born_on           DATE                                       NOT NULL,
    email             VARCHAR(255)                               NOT NULL,
    phone_number      VARCHAR(16)                                NOT NULL COMMENT 'E.164',
    created_at        DATETIME(6)                                NULL,
    PRIMARY KEY (id),
    INDEX idx_bookings_user_created (user_id, created_at),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
