"""
IoT Sensor Simulator
실제 IoT 센서처럼 서버 외부에서 POST /sensor-data 를 직접 호출합니다.

사용법:
    python simulator.py --device-id 1 --count 10 --interval 2 --threshold 80
    python simulator.py --device-id 1 --count 10 --interval 2 --threshold 80 --base-url http://localhost:8080

옵션:
    --device-id     장치 ID (필수)
    --count         전송 횟수 (기본값: 10, 최대: 100)
    --interval      전송 간격 초 (기본값: 2, 최소: 1)
    --threshold     임계값 — 랜덤 센서값 생성 기준 (기본값: 100.0)
    --base-url      서버 주소 (기본값: http://localhost:8080)
"""

import argparse
import random
import time
import sys

try:
    import requests
except ImportError:
    print("[오류] requests 라이브러리가 필요합니다: pip install requests")
    sys.exit(1)


def generate_value(threshold: float) -> float:
    """임계값 기준으로 랜덤 센서값 생성. 정상값 80% / 초과값 20%."""
    if random.random() < 0.8:
        value = random.uniform(0, threshold * 0.95)
    else:
        value = random.uniform(threshold * 1.01, threshold * 1.5)
    return round(value, 1)


def send_sensor_data(base_url: str, device_id: int, value: float) -> bool:
    url = f"{base_url}/sensor-data"
    payload = {"deviceId": device_id, "value": value}
    try:
        response = requests.post(url, json=payload, timeout=5)
        response.raise_for_status()
        return True
    except requests.exceptions.ConnectionError:
        print(f"  [오류] 서버에 연결할 수 없습니다: {base_url}")
        return False
    except requests.exceptions.Timeout:
        print(f"  [오류] 요청 타임아웃 (5초 초과)")
        return False
    except requests.exceptions.HTTPError as e:
        print(f"  [오류] HTTP {e.response.status_code}: {e.response.text}")
        return False


def run(device_id: int, count: int, interval: int, threshold: float, base_url: str):
    count = max(1, min(count, 100))
    interval = max(1, interval)

    print(f"IoT 시뮬레이터 시작")
    print(f"  장치 ID   : {device_id}")
    print(f"  전송 횟수  : {count}회")
    print(f"  전송 간격  : {interval}초")
    print(f"  임계값    : {threshold}")
    print(f"  서버 주소  : {base_url}")
    print("-" * 40)

    success = 0
    for i in range(1, count + 1):
        value = generate_value(threshold)
        status = "초과" if value > threshold else "정상"
        ok = send_sensor_data(base_url, device_id, value)

        if ok:
            success += 1
            print(f"  [{i:>3}/{count}] value={value:>7.1f}  {status}  -> 전송 완료")
        else:
            print(f"  [{i:>3}/{count}] value={value:>7.1f}  {status}  -> 전송 실패")

        if i < count:
            time.sleep(interval)

    print("-" * 40)
    print(f"완료: {success}/{count}건 전송 성공")


def main():
    parser = argparse.ArgumentParser(description="IoT 센서 데이터 시뮬레이터")
    parser.add_argument("--device-id",  type=int,   required=True,                   help="장치 ID")
    parser.add_argument("--count",      type=int,   default=10,                       help="전송 횟수 (기본: 10, 최대: 100)")
    parser.add_argument("--interval",   type=int,   default=2,                        help="전송 간격 초 (기본: 2, 최소: 1)")
    parser.add_argument("--threshold",  type=float, default=100.0,                    help="임계값 (기본: 100.0)")
    parser.add_argument("--base-url",   type=str,   default="http://localhost:8080",  help="서버 주소 (기본: http://localhost:8080)")
    args = parser.parse_args()

    run(
        device_id=args.device_id,
        count=args.count,
        interval=args.interval,
        threshold=args.threshold,
        base_url=args.base_url,
    )


if __name__ == "__main__":
    main()
