-- 공개 홈서버와 새 prod DB 가 함께 쓰는 데모 토폴로지다.
-- 인증 데이터가 아닌 공장·구역·물리 Device·측정 채널 구조만 관리한다.
-- 물리 Device 3개(엔진 유닛2, CNC 1호기) 아래 채널 7개 = simulator 의 공개 데이터 리플레이 채널과 같다.

INSERT INTO factories (name, description, created_at) VALUES
    ('엔진시험동', 'C-MAPSS 터보팬 엔진 시험 설비', CURRENT_TIMESTAMP),
    ('가공동',     'CNC 밀링 가공 설비',           CURRENT_TIMESTAMP);

INSERT INTO zones (factory_id, name, description, created_at) VALUES
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진1구역', '엔진 unit 1 시험 라인', CURRENT_TIMESTAMP),
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진2구역', '엔진 unit 2 시험 라인', CURRENT_TIMESTAMP),
    ((SELECT id FROM factories WHERE name = '가공동'),     '밀링1구역', 'CNC 밀링 1호기 라인',   CURRENT_TIMESTAMP);

-- 물리 Device: 측정 종류·임계값은 갖지 않는다(채널 경계). 기대 수신 주기만 노드가 가진다.
INSERT INTO device (
    zone_id, code, name, location, expected_interval_seconds, created_at, updated_at
) VALUES
    ((SELECT id FROM zones WHERE name = '엔진1구역'), 'CMAPSS-U1', '엔진 유닛1', 'C-MAPSS unit1', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), 'CMAPSS-U2', '엔진 유닛2', 'C-MAPSS unit2', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC-EXP01', 'CNC 1호기',  'CNC exp01',     10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 측정 채널: quantity_kind 는 enum 이 아닌 소문자 문자열, 임계 방향은 데모 전부 ABOVE.
INSERT INTO sensor_channel (
    device_id, code, unit, quantity_kind, threshold_value, threshold_direction, created_at, updated_at
) VALUES
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's4',                   '°R',    'temperature',  1416.0, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's11',                  'psia',  'pressure',       47.8, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's4',                   '°R',    'temperature',  1416.0, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's11',                  'psia',  'pressure',       47.8, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'S1_OutputPower',       'kW',    'power',          0.25, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'S1_CurrentFeedback',   'A',     'current',        30.0, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'X1_ActualAcceleration','mm/s²', 'acceleration',  900.0, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
