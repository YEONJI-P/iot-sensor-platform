-- =============================================================================
-- IoT Sensor Platform — 초기 데이터 시드 스크립트
-- =============================================================================
-- 대상: factories, zones, users, zone_users, device
-- 방식 A(채널=Device): 실측 데이터의 센서 채널 하나를 Device 하나로 등록한다.
--   Device ID(삽입 순서)는 services/simulator/simulator.py 의 리플레이 매핑과 일치해야 한다.
-- 비밀번호: pgcrypto의 BCrypt(strength=10) — Spring BCryptPasswordEncoder 호환
--
-- 실행 전: 테이블이 생성된 상태(Spring Boot 기동 후)여야 하며, 중복 실행은
--   UNIQUE 제약 위반이 난다. 재실행은 하단 TRUNCATE 주석을 먼저 실행한다.
--
--   psql -U postgres -d sensor_monitor_db -f services/simulator/seed.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- =============================================================================
-- 1. Factories
-- =============================================================================
INSERT INTO factories (name, description, created_at) VALUES
    ('엔진시험동', 'C-MAPSS 터보팬 엔진 시험 설비', NOW()),
    ('가공동',     'CNC 밀링 가공 설비',            NOW());


-- =============================================================================
-- 2. Zones
-- =============================================================================
INSERT INTO zones (factory_id, name, description, created_at) VALUES
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진1구역', '엔진 unit 1 시험 라인', NOW()),
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진2구역', '엔진 unit 2 시험 라인', NOW()),
    ((SELECT id FROM factories WHERE name = '가공동'),     '밀링1구역', 'CNC 밀링 1호기 라인',   NOW());


-- =============================================================================
-- 3. Users  (password: BCrypt strength=10)
-- =============================================================================
-- 계정 = {공장}-{역할}로 직관화. 비밀번호는 역할별(admin1234! / op1234! / view1234!).
INSERT INTO users (employee_id, name, email, password, role, status, factory_id, created_at, updated_at) VALUES
    ('SYSTEM',    '시스템 관리자',   'system@sensor.local',    crypt('admin1234!', gen_salt('bf', 10)), 'SYSTEM_ADMIN',  'ACTIVE', NULL, NOW(), NOW()),
    ('ENG-ADMIN', '엔진동 관리자',   'eng-admin@sensor.local', crypt('admin1234!', gen_salt('bf', 10)), 'FACTORY_ADMIN', 'ACTIVE', (SELECT id FROM factories WHERE name = '엔진시험동'), NOW(), NOW()),
    ('CNC-ADMIN', '가공동 관리자',   'cnc-admin@sensor.local', crypt('admin1234!', gen_salt('bf', 10)), 'FACTORY_ADMIN', 'ACTIVE', (SELECT id FROM factories WHERE name = '가공동'),     NOW(), NOW()),
    ('ENG-OP',    '엔진동 설비담당', 'eng-op@sensor.local',    crypt('op1234!',    gen_salt('bf', 10)), 'MEMBER',        'ACTIVE', (SELECT id FROM factories WHERE name = '엔진시험동'), NOW(), NOW()),
    ('CNC-OP',    '가공동 설비담당', 'cnc-op@sensor.local',    crypt('op1234!',    gen_salt('bf', 10)), 'MEMBER',        'ACTIVE', (SELECT id FROM factories WHERE name = '가공동'),     NOW(), NOW()),
    ('ENG-VIEW',  '엔진동 열람',     'eng-view@sensor.local',  crypt('view1234!',  gen_salt('bf', 10)), 'VIEWER',        'ACTIVE', (SELECT id FROM factories WHERE name = '엔진시험동'), NOW(), NOW()),
    ('CNC-VIEW',  '가공동 열람',     'cnc-view@sensor.local',  crypt('view1234!',  gen_salt('bf', 10)), 'VIEWER',        'ACTIVE', (SELECT id FROM factories WHERE name = '가공동'),     NOW(), NOW());


-- =============================================================================
-- 4. Zone Users  (구역 스코프 접근제어 데모)
-- =============================================================================
INSERT INTO zone_users (zone_id, user_id, created_at) VALUES
    -- ENG-OP: 엔진1, 엔진2구역
    ((SELECT id FROM zones WHERE name = '엔진1구역'), (SELECT id FROM users WHERE employee_id = 'ENG-OP'), NOW()),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), (SELECT id FROM users WHERE employee_id = 'ENG-OP'), NOW()),
    -- CNC-OP: 밀링1구역
    ((SELECT id FROM zones WHERE name = '밀링1구역'), (SELECT id FROM users WHERE employee_id = 'CNC-OP'), NOW()),
    -- ENG-VIEW: 엔진1, 엔진2구역 (읽기 전용)
    ((SELECT id FROM zones WHERE name = '엔진1구역'), (SELECT id FROM users WHERE employee_id = 'ENG-VIEW'), NOW()),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), (SELECT id FROM users WHERE employee_id = 'ENG-VIEW'), NOW()),
    -- CNC-VIEW: 밀링1구역 (읽기 전용)
    ((SELECT id FROM zones WHERE name = '밀링1구역'), (SELECT id FROM users WHERE employee_id = 'CNC-VIEW'), NOW());


-- =============================================================================
-- 5. Devices  (채널=Device. ID 순서 = simulator.py 리플레이 매핑)
--    threshold_value 는 실데이터 건강구간 분포에서 산출(대략치, 튜닝 가능)
-- =============================================================================
INSERT INTO device (zone_id, name, type, location, threshold_value, expected_interval_seconds) VALUES
    -- 1,2: C-MAPSS 엔진 unit 1 (s4 온도, s11 압력)
    ((SELECT id FROM zones WHERE name = '엔진1구역'), '엔진1-온도(s4)',  'TEMPERATURE',  'C-MAPSS unit1 s4',  1416.0, 10),
    ((SELECT id FROM zones WHERE name = '엔진1구역'), '엔진1-압력(s11)', 'PRESSURE',     'C-MAPSS unit1 s11', 47.8,   10),
    -- 3,4: C-MAPSS 엔진 unit 2
    ((SELECT id FROM zones WHERE name = '엔진2구역'), '엔진2-온도(s4)',  'TEMPERATURE',  'C-MAPSS unit2 s4',  1416.0, 10),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), '엔진2-압력(s11)', 'PRESSURE',     'C-MAPSS unit2 s11', 47.8,   10),
    -- 5,6,7: CNC 밀링 1호기 (스핀들 파워·전류, X축 가속)
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-스핀들파워', 'POWER',        'CNC exp01 S1_OutputPower',        0.25,  10),
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-스핀들전류', 'CURRENT',      'CNC exp01 S1_CurrentFeedback',    30.0,  10),
    -- 단측 비교라 양의 극단 스파이크만 감지(음의 편위 미감지) — 데모용 단순화
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-X축가속',    'ACCELERATION', 'CNC exp01 X1_ActualAcceleration', 900.0, 10);


-- =============================================================================
-- 전체 초기화 (재실행 필요 시 먼저 실행 후 위 INSERT 재실행)
-- =============================================================================
-- TRUNCATE alert, failed_reading, sensor_data, zone_users, device, zones, users, factories RESTART IDENTITY CASCADE;
