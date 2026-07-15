"""최소 검증 테스트.

기본 provider가 echo이므로 LLM 키 없이도 통과해야 한다.
"""

from fastapi.testclient import TestClient


def test_health_returns_ok():
    """헬스 엔드포인트가 200/ok를 반환한다."""
    from app.main import app

    client = TestClient(app)
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


def test_explain_anomaly_with_echo_provider():
    """이상 근거·권고 엔드포인트가 echo provider로 200을 반환한다."""
    from app.main import app

    client = TestClient(app)
    res = client.post(
        "/ax/explain-anomaly",
        json={
            "deviceName": "온도센서-A1",
            "sensorType": "TEMPERATURE",
            "value": 95.0,
            "threshold": 80.0,
        },
    )
    assert res.status_code == 200
    body = res.json()
    assert body["evidence"]
    assert body["recommendation"]
    assert body["model"] == "echo-0"


def test_explain_anomaly_rejects_missing_value():
    """필수 필드(value) 누락은 422로 거부된다."""
    from app.main import app

    client = TestClient(app)
    res = client.post("/ax/explain-anomaly", json={"deviceName": "온도센서-A1"})
    assert res.status_code == 422
