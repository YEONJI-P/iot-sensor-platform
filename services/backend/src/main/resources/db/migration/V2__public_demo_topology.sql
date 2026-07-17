-- 공개 홈서버와 새 prod DB가 함께 사용하는 데모 토폴로지다.
-- 이 migration은 인증 데이터가 아닌 공장·구역·장치 구조만 관리한다.

INSERT INTO factories (name, description, created_at) VALUES
    ('엔진시험동', 'C-MAPSS 터보팬 엔진 시험 설비', CURRENT_TIMESTAMP),
    ('가공동',     'CNC 밀링 가공 설비',           CURRENT_TIMESTAMP);

INSERT INTO zones (factory_id, name, description, created_at) VALUES
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진1구역', '엔진 unit 1 시험 라인', CURRENT_TIMESTAMP),
    ((SELECT id FROM factories WHERE name = '엔진시험동'), '엔진2구역', '엔진 unit 2 시험 라인', CURRENT_TIMESTAMP),
    ((SELECT id FROM factories WHERE name = '가공동'),     '밀링1구역', 'CNC 밀링 1호기 라인',   CURRENT_TIMESTAMP);

-- 새 DB에서의 삽입 순서는 simulator.py의 공개 데이터 리플레이 채널 1~7과 같다.
INSERT INTO device (
    zone_id, name, type, location, threshold_value, expected_interval_seconds, created_at, updated_at
) VALUES
    ((SELECT id FROM zones WHERE name = '엔진1구역'), '엔진1-온도(s4)',  'TEMPERATURE',  'C-MAPSS unit1 s4',                    1416.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '엔진1구역'), '엔진1-압력(s11)', 'PRESSURE',     'C-MAPSS unit1 s11',                     47.8, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), '엔진2-온도(s4)',  'TEMPERATURE',  'C-MAPSS unit2 s4',                    1416.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '엔진2구역'), '엔진2-압력(s11)', 'PRESSURE',     'C-MAPSS unit2 s11',                     47.8, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-스핀들파워', 'POWER',        'CNC exp01 S1_OutputPower',               0.25, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-스핀들전류', 'CURRENT',      'CNC exp01 S1_CurrentFeedback',          30.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM zones WHERE name = '밀링1구역'), 'CNC1-X축가속',    'ACCELERATION', 'CNC exp01 X1_ActualAcceleration',     900.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
