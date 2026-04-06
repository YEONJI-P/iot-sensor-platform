-- =============================================================================
-- IoT Sensor Platform — 초기 데이터 시드 스크립트
-- =============================================================================
-- 대상: organizations, org_groups, users, group_users, devices
-- 비밀번호: pgcrypto의 BCrypt(strength=10) 사용 — Spring BCryptPasswordEncoder 호환
--
-- 실행 전 주의사항
--   - 테이블이 이미 생성된 상태(Spring Boot 기동 후 DDL 적용 완료)여야 합니다.
--   - 중복 실행 시 UNIQUE 제약(employeeId, email) 위반으로 오류가 발생합니다.
--     재실행이 필요하면 하단 "전체 초기화" 섹션을 먼저 실행하세요.
--
-- ── 로컬 DB 실행 ──────────────────────────────────────────────────────────────
--   psql -U postgres -d iot_sensor_db_v2 -f seed.sql
--
-- ── Cloud SQL 실행 (Cloud SQL Auth Proxy 사용) ────────────────────────────────
--   1. Cloud SQL Auth Proxy 기동
--      cloud-sql-proxy <INSTANCE_CONNECTION_NAME> --port 5433
--
--   2. psql로 접속 후 실행
--      psql "host=127.0.0.1 port=5433 dbname=iot_sensor_db_v2 user=postgres" -f seed.sql
--
-- ── Cloud SQL 실행 (gcloud CLI 직접 접속) ─────────────────────────────────────
-- --   gcloud sql connect <INSTANCE_NAME> --user=postgres --database=iot_sensor_db_v2
-- --   -- 접속 후 \i seed.sql 또는 내용을 직접 붙여넣기
--
-- =============================================================================


-- BCrypt 확장 활성화
CREATE
EXTENSION IF NOT EXISTS pgcrypto;


-- =============================================================================
-- 1. Organizations
-- =============================================================================
INSERT INTO organizations (name, description, created_at) VALUES
    ('스마트공장A', '메인 생산 공장 (A동)', NOW()),
    ('스마트공장B', '신규 증설 공장 (B동)', NOW());


-- =============================================================================
-- 2. Org Groups
-- =============================================================================
INSERT INTO org_groups (organization_id, name, description, created_at) VALUES
    ((SELECT id FROM organizations WHERE name = '스마트공장A'), '1구역(생산라인)', 'A동 주요 생산 공정',         NOW()),
    ((SELECT id FROM organizations WHERE name = '스마트공장A'), '2구역(품질검사)', 'A동 제품 품질 검사실',       NOW()),
    ((SELECT id FROM organizations WHERE name = '스마트공장A'), '3구역(자재창고)', 'A동 원자재 및 부품 보관소',   NOW()),
    ((SELECT id FROM organizations WHERE name = '스마트공장B'), '4구역(포장라인)', 'B동 제품 포장 및 검수',       NOW()),
    ((SELECT id FROM organizations WHERE name = '스마트공장B'), '5구역(출하장)',   'B동 완제품 출하 대기 구역',   NOW());


-- =============================================================================
-- 3. Users  (password: BCrypt strength=10)
-- =============================================================================
INSERT INTO users (employee_id, name, email, password, role, status, organization_id, created_at, updated_at) VALUES
    ('ADMIN001', '슈퍼관리자',    'admin@factory.com',  crypt('admin1234!', gen_salt('bf', 10)), 'SUPER_ADMIN',    'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('MGR001',   '공장A-관리자', 'mgr_a@factory.com',  crypt('mgr1234!',   gen_salt('bf', 10)), 'USER_ADMIN',     'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('MGR002',   '공장B-관리자', 'mgr_b@factory.com',  crypt('mgr1234!',   gen_salt('bf', 10)), 'USER_ADMIN',     'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장B'), NOW(), NOW()),
    ('DEV001',   '장치담당자A',  'dev01@factory.com',  crypt('dev1234!',   gen_salt('bf', 10)), 'DEVICE_MANAGER', 'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('INP001',   '데이터입력자A1','inp01@factory.com', crypt('inp1234!',   gen_salt('bf', 10)), 'DATA_INPUTTER',  'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('INP002',   '데이터입력자A2','inp02@factory.com', crypt('inp1234!',   gen_salt('bf', 10)), 'DATA_INPUTTER',  'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('INP003',   '데이터입력자B1','inp03@factory.com', crypt('inp1234!',   gen_salt('bf', 10)), 'DATA_INPUTTER',  'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장B'), NOW(), NOW()),
    ('ANL001',   '분석담당자A',  'anl01@factory.com',  crypt('anl1234!',   gen_salt('bf', 10)), 'DATA_ANALYST',   'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('VWR001',   '열람자A',      'vwr01@factory.com',  crypt('vwr1234!',   gen_salt('bf', 10)), 'VIEWER',         'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장A'), NOW(), NOW()),
    ('VWR002',   '열람자B',      'vwr02@factory.com',  crypt('vwr1234!',   gen_salt('bf', 10)), 'VIEWER',         'ACTIVE', (SELECT id FROM organizations WHERE name = '스마트공장B'), NOW(), NOW());


-- =============================================================================
-- 4. Group Users  (initial-data.json의 groupNames 매핑)
-- =============================================================================
INSERT INTO group_users (group_id, user_id, created_at) VALUES
    -- DEV001: 1구역, 2구역
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), (SELECT id FROM users WHERE employee_id = 'DEV001'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '2구역(품질검사)'), (SELECT id FROM users WHERE employee_id = 'DEV001'), NOW()),
    -- INP001: 1구역
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), (SELECT id FROM users WHERE employee_id = 'INP001'), NOW()),
    -- INP002: 3구역
    ((SELECT id FROM org_groups WHERE name = '3구역(자재창고)'), (SELECT id FROM users WHERE employee_id = 'INP002'), NOW()),
    -- INP003: 4구역, 5구역
    ((SELECT id FROM org_groups WHERE name = '4구역(포장라인)'), (SELECT id FROM users WHERE employee_id = 'INP003'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '5구역(출하장)'),   (SELECT id FROM users WHERE employee_id = 'INP003'), NOW()),
    -- ANL001: 1구역, 2구역
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), (SELECT id FROM users WHERE employee_id = 'ANL001'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '2구역(품질검사)'), (SELECT id FROM users WHERE employee_id = 'ANL001'), NOW()),
    -- VWR001: 1구역, 2구역, 3구역
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), (SELECT id FROM users WHERE employee_id = 'VWR001'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '2구역(품질검사)'), (SELECT id FROM users WHERE employee_id = 'VWR001'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '3구역(자재창고)'), (SELECT id FROM users WHERE employee_id = 'VWR001'), NOW()),
    -- VWR002: 4구역, 5구역
    ((SELECT id FROM org_groups WHERE name = '4구역(포장라인)'), (SELECT id FROM users WHERE employee_id = 'VWR002'), NOW()),
    ((SELECT id FROM org_groups WHERE name = '5구역(출하장)'),   (SELECT id FROM users WHERE employee_id = 'VWR002'), NOW());


-- =============================================================================
-- 5. Devices
-- =============================================================================
INSERT INTO device (group_id, name, type, location, threshold_value) VALUES
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), '온도센서-A1', 'TEMPERATURE', '1구역-생산-01', 80.0),
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), '진동센서-A1', 'VIBRATION',   '1구역-생산-02', 50.0),
    ((SELECT id FROM org_groups WHERE name = '1구역(생산라인)'), '조도센서-A1', 'ILLUMINANCE', '1구역-조명-01', 300.0),
    ((SELECT id FROM org_groups WHERE name = '2구역(품질검사)'), '압력센서-A2', 'PRESSURE',    '2구역-검사-01', 1.2),
    ((SELECT id FROM org_groups WHERE name = '2구역(품질검사)'), '온도센서-A2', 'TEMPERATURE', '2구역-검사-02', 70.0),
    ((SELECT id FROM org_groups WHERE name = '3구역(자재창고)'), '조도센서-A3', 'ILLUMINANCE', '3구역-창고-01', 200.0),
    ((SELECT id FROM org_groups WHERE name = '4구역(포장라인)'), '온도센서-B4', 'TEMPERATURE', '4구역-포장-01', 75.0),
    ((SELECT id FROM org_groups WHERE name = '4구역(포장라인)'), '진동센서-B4', 'VIBRATION',   '4구역-포장-02', 45.0),
    ((SELECT id FROM org_groups WHERE name = '5구역(출하장)'),   '압력센서-B5', 'PRESSURE',    '5구역-출하-01', 1.0),
    ((SELECT id FROM org_groups WHERE name = '5구역(출하장)'),   '조도센서-B5', 'ILLUMINANCE', '5구역-출하-02', 250.0);


-- =============================================================================
-- 전체 초기화 (재실행 필요 시 이 섹션을 먼저 실행 후 위 INSERT 재실행)
-- =============================================================================
-- TRUNCATE alerts, sensor_data, group_users, device, org_groups, users, organizations RESTART IDENTITY CASCADE;
