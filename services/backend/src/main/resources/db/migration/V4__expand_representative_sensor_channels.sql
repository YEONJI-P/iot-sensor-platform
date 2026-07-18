-- 대표 공개 채널을 3 device / 20 channel 로 확장한다. 적용된 V1~V3는 수정하지 않는다.
--
-- C-MAPSS 신규 임계값은 FD001의 모든 unit에서 각 unit 수명 초기 20% 행을 건강 기준으로
-- 모은 뒤 열화 방향의 단측 99.5 percentile(하락 채널은 0.5 percentile)을 원본 정밀도에
-- 맞춰 반올림했다: s2 643.4(상승), s7 552.4(하락), s15 8.485(상승), s21 23.165(하락).
-- 기존 공개 계약인 s4 1416 ABOVE, s11 47.8 ABOVE는 변경하지 않는다.
--
-- CNC experiment01의 abs(value) 99.5 percentile을 표시 단위로 반올림했다:
-- X/Y/Z ActualAcceleration = 800/500/1000, X1_CurrentFeedback = 14.
-- S1_ActualVelocity와 M1_CURRENT_FEEDRATE는 단위·임계값을 추정하지 않는 표시 전용 채널이다.

ALTER TABLE sensor_channel DROP CONSTRAINT ck_sensor_channel_direction;
ALTER TABLE sensor_channel ADD CONSTRAINT ck_sensor_channel_direction
    CHECK (threshold_direction IN ('ABOVE', 'BELOW', 'ABS_ABOVE'));

INSERT INTO sensor_channel (
    device_id, code, unit, quantity_kind, threshold_value, threshold_direction, created_at, updated_at
) VALUES
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's2',  '°R',    'temperature', 643.4,  'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's7',  'psia',  'pressure',    552.4,  'BELOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's15', NULL,    'ratio',          8.485, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U1'), 's21', 'lbm/s', 'coolant_flow', 23.165, 'BELOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's2',  '°R',    'temperature', 643.4,  'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's7',  'psia',  'pressure',    552.4,  'BELOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's15', NULL,    'ratio',          8.485, 'ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CMAPSS-U2'), 's21', 'lbm/s', 'coolant_flow', 23.165, 'BELOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'Y1_ActualAcceleration', 'mm/s²', 'acceleration', 500.0,  'ABS_ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'Z1_ActualAcceleration', 'mm/s²', 'acceleration', 1000.0, 'ABS_ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'X1_CurrentFeedback',    'A',     'current',        14.0, 'ABS_ABOVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'S1_ActualVelocity',     NULL,    'velocity',       NULL,  NULL,        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM device WHERE code = 'CNC-EXP01'), 'M1_CURRENT_FEEDRATE',   NULL,    'feedrate',       NULL,  NULL,        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE sensor_channel
SET threshold_value = 800.0,
    threshold_direction = 'ABS_ABOVE',
    updated_at = CURRENT_TIMESTAMP
WHERE device_id = (SELECT id FROM device WHERE code = 'CNC-EXP01')
  AND code = 'X1_ActualAcceleration';
