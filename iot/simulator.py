"""
IoT Sensor Simulator
실제 IoT 센서처럼 서버 외부에서 POST /sensor-data 를 직접 호출합니다.
여러 장치를 multiprocessing.Pool 로 병렬 실행합니다.
"""

import argparse
import random
import sys
import time
from multiprocessing import Pool

try:
    import requests
except ImportError:
    print("[오류] requests 라이브러리가 필요합니다: pip install requests")
    sys.exit(1)


# =============================================================================
# 1. Static 설정
# =============================================================================

VALUE_RANGES = {
    "TEMPERATURE": {"min": 0.0,  "max": 120.0, "default_threshold": 80.0},
    "VIBRATION":   {"min": 0.0,  "max": 100.0, "default_threshold": 50.0},
    "ILLUMINANCE": {"min": 0.0,  "max": 2000.0,"default_threshold": 300.0},
    "PRESSURE":    {"min": 0.0,  "max": 2.0,   "default_threshold": 1.2},
}

# seed.sql 기준 device ID → (type, threshold) 매핑
ALL_PRESET = [
    {"id": 1,  "type": "TEMPERATURE", "threshold": 80.0},
    {"id": 2,  "type": "VIBRATION",   "threshold": 50.0},
    {"id": 3,  "type": "ILLUMINANCE", "threshold": 300.0},
    {"id": 4,  "type": "PRESSURE",    "threshold": 1.2},
    {"id": 5,  "type": "TEMPERATURE", "threshold": 70.0},
    {"id": 6,  "type": "ILLUMINANCE", "threshold": 200.0},
    {"id": 7,  "type": "TEMPERATURE", "threshold": 75.0},
    {"id": 8,  "type": "VIBRATION",   "threshold": 45.0},
    {"id": 9,  "type": "PRESSURE",    "threshold": 1.0},
    {"id": 10, "type": "ILLUMINANCE", "threshold": 250.0},
]


# =============================================================================
# 2. Type별 값 생성 함수
#    - 정상값 80% / 임계값 초과 20% 기준
#    - 각 타입의 물리적 특성 반영
# =============================================================================

def generate_temperature(threshold: float) -> float:
    """완만한 uniform 분포. 정상 구간 0 ~ threshold*0.95."""
    r = VALUE_RANGES["TEMPERATURE"]
    if random.random() < 0.8:
        return round(random.uniform(r["min"], threshold * 0.95), 1)
    return round(random.uniform(threshold * 1.01, r["max"]), 1)


def generate_vibration(threshold: float) -> float:
    """정상 구간은 낮게 유지, 이상 시 급등하는 스파이크 모사."""
    r = VALUE_RANGES["VIBRATION"]
    if random.random() < 0.75:
        return round(random.uniform(r["min"], threshold * 0.6), 1)
    return round(random.uniform(threshold * 1.05, r["max"]), 1)


def generate_illuminance(threshold: float) -> float:
    """주로 낮은 값(어두운 환경), 가끔 고조도 발생."""
    r = VALUE_RANGES["ILLUMINANCE"]
    if random.random() < 0.85:
        return round(random.uniform(r["min"], threshold * 0.9), 1)
    return round(random.uniform(threshold * 1.01, r["max"]), 1)


def generate_pressure(threshold: float) -> float:
    """uniform 기반, 초과 시 급격히 높은 값."""
    r = VALUE_RANGES["PRESSURE"]
    if random.random() < 0.8:
        return round(random.uniform(r["min"], threshold * 0.95), 3)
    return round(random.uniform(threshold * 1.1, r["max"]), 3)


GENERATORS = {
    "TEMPERATURE": generate_temperature,
    "VIBRATION":   generate_vibration,
    "ILLUMINANCE": generate_illuminance,
    "PRESSURE":    generate_pressure,
}


# =============================================================================
# 3. HTTP 전송
# =============================================================================

def send(base_url: str, device_id: int, value: float) -> bool:
    try:
        response = requests.post(
            f"{base_url}/sensor-data",
            json={"deviceId": device_id, "value": value},
            timeout=5,
        )
        response.raise_for_status()
        return True
    except requests.exceptions.ConnectionError:
        print(f"  [장치 {device_id}] [오류] 서버 연결 실패: {base_url}")
        return False
    except requests.exceptions.Timeout:
        print(f"  [장치 {device_id}] [오류] 요청 타임아웃")
        return False
    except requests.exceptions.HTTPError as e:
        print(f"  [장치 {device_id}] [오류] HTTP {e.response.status_code}: {e.response.text}")
        return False


# =============================================================================
# 4. 프로세스 워커 (Pool.starmap 에서 각 장치별로 실행)
# =============================================================================

def worker(device_id: int, sensor_type: str, threshold: float, count: int, interval: int, base_url: str):
    generate = GENERATORS[sensor_type]
    success = 0

    print(f"[장치 {device_id}:{sensor_type}] 시작 — {count}회 / {interval}초 간격 / 임계값 {threshold}")

    for i in range(1, count + 1):
        value = generate(threshold)
        status = "초과" if value > threshold else "정상"
        ok = send(base_url, device_id, value)

        if ok:
            success += 1
            print(f"  [장치 {device_id}] [{i:>3}/{count}] {value:>8}  {status}  -> 전송 완료")
        else:
            print(f"  [장치 {device_id}] [{i:>3}/{count}] {value:>8}  {status}  -> 전송 실패")

        if i < count:
            time.sleep(interval)

    print(f"[장치 {device_id}:{sensor_type}] 완료 — {success}/{count}건 성공")


# =============================================================================
# 5. main
#
# CLI 사용 예시:
#
#   [단일 장치]
#   python simulator.py --devices 1:TEMPERATURE
#
#   [여러 장치 병렬 실행]
#   python simulator.py --devices 1:TEMPERATURE 2:VIBRATION 3:ILLUMINANCE 4:PRESSURE
#
#   [전송 횟수·간격 지정]
#   python simulator.py --devices 1:TEMPERATURE 2:VIBRATION --count 30 --interval 1
#
#   [--all 프리셋: seed.sql 기준 전체 10개 장치 동시 실행]
#   python simulator.py --all
#   python simulator.py --all --count 50 --interval 2
#
#   [배포 서버 대상]
#   python simulator.py --all --base-url https://iot-sensor-platform-142990968320.asia-northeast3.run.app
#
# 인자:
#   --devices   id:TYPE 형식으로 1개 이상 (--all 사용 시 생략)
#               TYPE: TEMPERATURE | VIBRATION | ILLUMINANCE | PRESSURE
#   --all       seed.sql 기준 전체 10개 장치를 프리셋 threshold로 실행
#   --count     장치당 전송 횟수 (기본: 20)
#   --interval  전송 간격 초 (기본: 2, 최소: 1)
#   --base-url  서버 주소 (기본: http://localhost:8080)
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="IoT 센서 데이터 시뮬레이터 — multiprocessing 병렬 실행",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--devices", nargs="+", metavar="ID:TYPE",
        help="장치 목록 (예: 1:TEMPERATURE 2:VIBRATION). --all 사용 시 생략 가능",
    )
    parser.add_argument(
        "--all", action="store_true",
        help="seed.sql 기준 전체 10개 장치를 프리셋 threshold로 실행",
    )
    parser.add_argument("--count",    type=int, default=20,                      help="장치당 전송 횟수 (기본: 20)")
    parser.add_argument("--interval", type=int, default=2,                       help="전송 간격 초 (기본: 2, 최소: 1)")
    parser.add_argument("--base-url", type=str, default="http://localhost:8080", help="서버 주소 (기본: http://localhost:8080)")
    args = parser.parse_args()

    if not args.all and not args.devices:
        parser.error("--devices 또는 --all 중 하나를 지정하세요.")

    interval = max(1, args.interval)
    count    = max(1, args.count)

    # 실행 대상 목록 구성
    if args.all:
        targets = [
            (d["id"], d["type"], d["threshold"], count, interval, args.base_url)
            for d in ALL_PRESET
        ]
    else:
        targets = []
        valid_types = set(GENERATORS.keys())
        for token in args.devices:
            try:
                raw_id, raw_type = token.split(":")
                device_id   = int(raw_id)
                sensor_type = raw_type.upper()
            except ValueError:
                parser.error(f"잘못된 형식: '{token}' — 올바른 형식: ID:TYPE (예: 1:TEMPERATURE)")

            if sensor_type not in valid_types:
                parser.error(f"지원하지 않는 타입: '{sensor_type}' — 사용 가능: {', '.join(valid_types)}")

            threshold = VALUE_RANGES[sensor_type]["default_threshold"]
            targets.append((device_id, sensor_type, threshold, count, interval, args.base_url))

    print(f"시뮬레이터 시작 — 장치 {len(targets)}개 / 장치당 {count}회 / {interval}초 간격")
    print(f"서버: {args.base_url}")
    print("=" * 50)

    with Pool(processes=len(targets)) as pool:
        pool.starmap(worker, targets)

    print("=" * 50)
    print("전체 완료")


if __name__ == "__main__":
    main()
