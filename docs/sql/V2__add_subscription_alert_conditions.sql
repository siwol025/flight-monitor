-- GAP-1: 알림 조건 커스터마이징 — Subscription 알림 조건 필드 추가
-- 적용 순서: DB ALTER 먼저 실행 → 서버 배포 (ddl-auto: validate 환경)
ALTER TABLE subscriptions
    ADD COLUMN target_price         DECIMAL(12, 2) NULL COMMENT '목표 가격 (null이면 조건 없음)',
    ADD COLUMN drop_threshold_percent DECIMAL(5, 2)  NULL COMMENT '하락률 임계값 % (null이면 조건 없음)';
