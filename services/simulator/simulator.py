"""
IoT Sensor Simulator — 실측 데이터 리플레이
저장된 공개 센서 시계열(services/simulator/data/)을 시간 순으로 한 행씩 흘려보내(리플레이)
POST /sensor-data 로 전송해 실시간 수신을 재현한다.

데이터는 먼저 내려받아야 한다:  bash services/simulator/data/download.sh
장치(device_id)별 매핑은 services/simulator/seed.sql 의 device 삽입 순서와 일치한다(방식 A: 채널=Device).
"""

import argparse
import csv
import os
import sys
import time
from multiprocessing import Pool

try:
    import requests
except ImportError:
    print("[오류] requests 라이브러리가 필요합니다: pip install requests")
    sys.exit(1)


DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

# C-MAPSS train_FD001: 공백 구분, 헤더 없음. 컬럼 인덱스 = unit0, cycle1, set2~4, s1=5 ... s21=25
# s4 = index 8, s11 = index 15
REPLAY_PRESET = [
    {"id": 1, "kind": "cmapss", "unit": 1, "col": 8,  "label": "엔진1-온도(s4)"},
    {"id": 2, "kind": "cmapss", "unit": 1, "col": 15, "label": "엔진1-압력(s11)"},
    {"id": 3, "kind": "cmapss", "unit": 2, "col": 8,  "label": "엔진2-온도(s4)"},
    {"id": 4, "kind": "cmapss", "unit": 2, "col": 15, "label": "엔진2-압력(s11)"},
    {"id": 5, "kind": "cnc", "file": "cnc_experiment_01.csv", "col": "S1_OutputPower",        "label": "CNC1-스핀들파워"},
    {"id": 6, "kind": "cnc", "file": "cnc_experiment_01.csv", "col": "S1_CurrentFeedback",    "label": "CNC1-스핀들전류"},
    {"id": 7, "kind": "cnc", "file": "cnc_experiment_01.csv", "col": "X1_ActualAcceleration", "label": "CNC1-X축가속"},
]


# =============================================================================
# 1. 시리즈 로딩 (데이터셋별)
# =============================================================================

def load_cmapss_series(unit: int, col: int) -> list[float]:
    """C-MAPSS FD001 에서 해당 엔진(unit)의 col 채널을 사이클 순서로 추출한다."""
    path = os.path.join(DATA_DIR, "cmapss_train_FD001.txt")
    series = []
    with open(path) as f:
        for line in f:
            p = line.split()
            if len(p) < 26:
                continue
            if int(float(p[0])) == unit:
                series.append(float(p[col]))
    return series


def load_cnc_series(filename: str, col: str) -> list[float]:
    """CNC 실험 CSV 에서 col 채널을 행 순서로 추출한다."""
    path = os.path.join(DATA_DIR, filename)
    series = []
    with open(path) as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                series.append(float(row[col]))
            except (KeyError, ValueError):
                continue
    return series


def load_series(preset: dict) -> list[float]:
    if preset["kind"] == "cmapss":
        return load_cmapss_series(preset["unit"], preset["col"])
    return load_cnc_series(preset["file"], preset["col"])


# =============================================================================
# 2. HTTP 전송
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
# 3. 워커 (Pool 에서 장치별로 실행)
# =============================================================================

def worker(preset: dict, interval: float, limit: int, base_url: str):
    device_id = preset["id"]
    label = preset["label"]

    try:
        series = load_series(preset)
    except FileNotFoundError:
        print(f"[장치 {device_id}:{label}] 데이터 파일 없음 — 먼저 'bash services/simulator/data/download.sh' 실행")
        return

    if limit > 0:
        series = series[:limit]
    if not series:
        print(f"[장치 {device_id}:{label}] 리플레이할 데이터가 없습니다")
        return

    print(f"[장치 {device_id}:{label}] 리플레이 시작 — {len(series)}행 / {interval}초 간격")
    success = 0
    for i, value in enumerate(series, start=1):
        if send(base_url, device_id, value):
            success += 1
        if i % 20 == 0 or i == len(series):
            print(f"  [장치 {device_id}] {i}/{len(series)} 전송 (마지막 값 {value})")
        if i < len(series):
            time.sleep(interval)

    print(f"[장치 {device_id}:{label}] 완료 — {success}/{len(series)}건 성공")


# =============================================================================
# 4. main
#
# 사용 예시:
#   bash services/simulator/data/download.sh            # 데이터 먼저 내려받기
#   python services/simulator/simulator.py --all        # 7개 채널 전체 리플레이
#   python services/simulator/simulator.py --devices 1 2 --interval 0.5 --limit 100
#   python services/simulator/simulator.py --all --base-url http://localhost:23100
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="IoT 센서 시뮬레이터 — 실측 데이터 리플레이",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--devices", nargs="+", type=int, metavar="ID",
                        help="리플레이할 device id 목록 (기본: --all)")
    parser.add_argument("--all", action="store_true", help="전체 7개 채널 리플레이")
    parser.add_argument("--interval", type=float, default=1.0, help="행 간 간격 초 (기본 1.0)")
    parser.add_argument("--limit", type=int, default=0, help="장치당 최대 행 수 (기본 0=전체)")
    parser.add_argument("--base-url", type=str, default="http://localhost:23100", help="서버 주소")
    args = parser.parse_args()

    if not args.all and not args.devices:
        parser.error("--devices 또는 --all 중 하나를 지정하세요.")

    if args.all:
        presets = list(REPLAY_PRESET)
    else:
        by_id = {p["id"]: p for p in REPLAY_PRESET}
        presets = []
        for did in args.devices:
            if did not in by_id:
                parser.error(f"알 수 없는 device id: {did} (사용 가능: {sorted(by_id)})")
            presets.append(by_id[did])

    print(f"시뮬레이터 시작 — 채널 {len(presets)}개 / {args.interval}초 간격 / 서버 {args.base_url}")
    print("=" * 50)

    targets = [(p, args.interval, args.limit, args.base_url) for p in presets]
    with Pool(processes=len(targets)) as pool:
        pool.starmap(worker, targets)

    print("=" * 50)
    print("전체 완료")


if __name__ == "__main__":
    main()
