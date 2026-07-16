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


def test_explain_anomaly_includes_unit_in_evidence():
    """unit을 넘기면 근거 문장에 단위가 붙는다."""
    from app.main import app

    client = TestClient(app)
    res = client.post(
        "/ax/explain-anomaly",
        json={
            "deviceName": "온도센서-A1",
            "sensorType": "TEMPERATURE",
            "unit": "°C",
            "value": 95.0,
            "threshold": 80.0,
        },
    )
    assert res.status_code == 200
    assert "°C" in res.json()["evidence"]


def test_explain_anomaly_includes_window_metrics_in_evidence():
    """윈도우 지표를 넘기면 근거에 초과율·추세·변동성이 반영된다."""
    from app.main import app

    client = TestClient(app)
    res = client.post(
        "/ax/explain-anomaly",
        json={
            "deviceName": "엔진1-온도",
            "sensorType": "TEMPERATURE",
            "value": 1500.0,
            "threshold": 1416.0,
            "breachRate": 0.6,
            "trend": 8.0,
            "volatility": 12.3,
        },
    )
    assert res.status_code == 200
    evidence = res.json()["evidence"]
    assert "초과율 60%" in evidence
    assert "상승 추세" in evidence


def test_explain_anomaly_rejects_missing_value():
    """필수 필드(value) 누락은 422로 거부된다."""
    from app.main import app

    client = TestClient(app)
    res = client.post("/ax/explain-anomaly", json={"deviceName": "온도센서-A1"})
    assert res.status_code == 422
