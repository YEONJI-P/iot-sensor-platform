-- 임계값과 방향은 하나의 설정 계약이다. V1~V4에 남을 수 있는 부분 입력을
-- 현재 런타임 호환 규칙에 맞춰 정규화한 뒤 DB에서도 쌍을 강제한다.
UPDATE sensor_channel
SET threshold_direction = 'ABOVE',
    updated_at = CURRENT_TIMESTAMP
WHERE threshold_value IS NOT NULL
  AND threshold_direction IS NULL;

UPDATE sensor_channel
SET threshold_direction = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE threshold_value IS NULL
  AND threshold_direction IS NOT NULL;

ALTER TABLE sensor_channel
    ADD CONSTRAINT ck_sensor_channel_threshold_pair
    CHECK ((
        (threshold_value IS NULL AND threshold_direction IS NULL)
        OR (threshold_value IS NOT NULL AND threshold_direction IS NOT NULL)
    ) AND (threshold_direction IS DISTINCT FROM 'ABS_ABOVE' OR threshold_value > 0));
