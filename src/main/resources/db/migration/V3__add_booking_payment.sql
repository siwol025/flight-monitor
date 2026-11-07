-- 예매 상태와 결제 칼럼. 기존 행(간소화 예매의 mock 즉시 주문)은 TICKETED. MySQL DDL은 문장마다 암묵 커밋한다
ALTER TABLE bookings
    MODIFY provider_order_id VARCHAR(100) NULL,
    MODIFY booking_reference VARCHAR(20) NULL,
    ADD COLUMN status ENUM ('DUFFEL_PAYING', 'DUFFEL_PAYMENT_UNCONFIRMED', 'FAILED', 'HELD', 'HOLD_REQUESTED',
        'PAYMENT_APPROVED', 'PAYMENT_CONFIRMING', 'TICKETED') NOT NULL DEFAULT 'TICKETED',
    ADD COLUMN status_changed_at DATETIME(6) NULL,
    ADD COLUMN provider_passenger_id VARCHAR(50) NULL,
    ADD COLUMN payment_required_by DATETIME(6) NULL,
    ADD COLUMN charge_amount BIGINT NULL COMMENT 'KRW 정수',
    ADD COLUMN toss_order_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN toss_idempotency_key VARCHAR(64) NULL,
    ADD COLUMN payment_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN toss_approved_at DATETIME(6) NULL,
    ADD COLUMN duffel_paid_at DATETIME(6) NULL,
    ADD COLUMN ticket_numbers VARCHAR(255) NULL,
    ADD COLUMN failure_code VARCHAR(64) NULL;

-- 기본값은 기존 행을 채우는 데만 쓴다. 새 행은 상태를 반드시 정해서 넣는다
ALTER TABLE bookings ALTER COLUMN status DROP DEFAULT;

ALTER TABLE bookings
    ADD CONSTRAINT uk_bookings_toss_order UNIQUE (toss_order_id),
    ADD CONSTRAINT uk_bookings_payment_key UNIQUE (payment_key),
    ADD INDEX idx_bookings_status_changed (status, status_changed_at);

-- 우리 API 멱등키. 키를 한 번만 처리하고 결과를 저장해 재요청에 그대로 돌려준다
CREATE TABLE idempotency_keys
(
    id              BIGINT                                     NOT NULL AUTO_INCREMENT,
    user_id         BIGINT                                     NOT NULL,
    scope           VARCHAR(40)                                NOT NULL COMMENT 'BOOKING_CREATE, PAYMENT_CONFIRM',
    idem_key        VARCHAR(64)                                NOT NULL,
    request_hash    CHAR(64)                                   NOT NULL COMMENT '요청 본문 SHA-256',
    status          ENUM ('COMPLETED', 'FAILED', 'PROCESSING') NOT NULL,
    response_status INT                                        NULL,
    response_body   TEXT                                       NULL,
    booking_id      BIGINT                                     NULL,
    created_at      DATETIME(6)                                NOT NULL,
    updated_at      DATETIME(6)                                NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_keys UNIQUE (user_id, scope, idem_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
