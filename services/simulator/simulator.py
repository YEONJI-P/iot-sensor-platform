"""
IoT Sensor Simulator — 실측 데이터 리플레이
저장된 공개 센서 시계열(services/simulator/data/)을 시간 순으로 한 행씩 흘려보내(리플레이)
POST /sensor-data 로 전송해 실시간 수신을 재현한다.

데이터는 먼저 내려받아야 한다:  bash services/simulator/data/download.sh

수신 모델: 물리 Device 아래 여러 SensorChannel 이 있고, 원본 데이터의 한 행이
관측 한 묶음(batch)이 된다 — 원본 1행 = measurements map 1건 = POST 1건.
장치(deviceCode)·채널(channel code) 매핑은 services/simulator/seed.sql 의
device/sensor_channel 삽입 내용과 일치해야 한다.
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
# CNC 실험 CSV: 헤더 있음(DictReader), 채널 code = 원본 컬럼명 그대로 사용.
REPLAY_PRESET = [
    {
        "code": "CMAPSS-U1",
        "kind": "cmapss",
        "unit": 1,
        "channels": {"s4": 8, "s11": 15},
        "label": "엔진 유닛1",
    },
    {
        "code": "CMAPSS-U2",
        "kind": "cmapss",
        "unit": 2,
        "channels": {"s4": 8, "s11": 15},
        "label": "엔진 유닛2",
    },
    {
        "code": "CNC-EXP01",
        "kind": "cnc",
        "file": "cnc_experiment_01.csv",
        "channels": {
            "S1_OutputPower": "S1_OutputPower",
            "S1_CurrentFeedback": "S1_CurrentFeedback",
            "X1_ActualAcceleration": "X1_ActualAcceleration",
        },
        "label": "CNC 1호기",
    },
]


# =============================================================================
# 1. 배치 로딩 (데이터셋별) — (sourceSeq, measurements) 튜플의 리스트를 만든다.
# =============================================================================

def load_cmapss_batches(unit: int, channels: dict) -> list[tuple[int, dict]]:
    """C-MAPSS FD001 에서 해당 엔진(unit)의 행을 사이클 순서로 순회하며,
    channels(채널code→컬럼 인덱스)에 정의된 값을 모두 한 batch 로 묶는다.
    sourceSeq 는 2번째 컬럼(cycle)이다."""
    path = os.path.join(DATA_DIR, "cmapss_train_FD001.txt")
    batches = []
    with open(path) as f:
        for line in f:
            p = line.split()
            if len(p) < 26:
                continue
            if int(float(p[0])) != unit:
                continue
            cycle = int(float(p[1]))
            measurements = {name: float(p[col]) for name, col in channels.items()}
            batches.append((cycle, measurements))
    return batches


def load_cnc_batches(filename: str, channels: dict) -> list[tuple[int, dict]]:
    """CNC 실험 CSV 를 행 순서로 순회하며, channels(채널code→원본 컬럼명)에
    정의된 값을 모두 한 batch 로 묶는다. sourceSeq 는 행 인덱스(0부터)이다."""
    path = os.path.join(DATA_DIR, filename)
    batches = []
    with open(path) as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader):
            try:
                measurements = {name: float(row[col]) for name, col in channels.items()}
            except (KeyError, ValueError):
                continue
            batches.append((idx, measurements))
    return batches


def load_batches(preset: dict) -> list[tuple[int, dict]]:
    if preset["kind"] == "cmapss":
        return load_cmapss_batches(preset["unit"], preset["channels"])
    return load_cnc_batches(preset["file"], preset["channels"])


# =============================================================================
# 2. HTTP 전송
# =============================================================================

def send(base_url: str, device_code: str, source_seq: int, measurements: dict) -> bool:
    payload = {
        "deviceCode": device_code,
        "sourceSeq": source_seq,
        "measurements": measurements,
    }
    try:
        response = requests.post(f"{base_url}/sensor-data", json=payload, timeout=5)
    except requests.exceptions.ConnectionError:
        print(f"  [{device_code}] [오류] 서버 연결 실패: {base_url}")
        return False
    except requests.exceptions.Timeout:
        print(f"  [{device_code}] [오류] 요청 타임아웃")
        return False

    # 404(deviceCode 미존재) / 422(전 채널 미지) / 400(validation) 모두 응답 본문을 로그로
    # 남기고 다음 행으로 넘어간다 — 같은 행에 대한 재시도는 하지 않는다.
    if response.status_code >= 400:
        print(f"  [{device_code}] [오류] HTTP {response.status_code}: {response.text}")
        return False
    return True


# =============================================================================
# 3. 워커 (Pool 에서 물리 device 별로 실행)
# =============================================================================

def worker(preset: dict, interval: float, limit: int, base_url: str):
    device_code = preset["code"]
    label = preset.get("label", device_code)

    try:
        batches = load_batches(preset)
    except FileNotFoundError:
        print(f"[{device_code}:{label}] 데이터 파일 없음 — 먼저 'bash services/simulator/data/download.sh' 실행")
        return

    if limit > 0:
        batches = batches[:limit]
    if not batches:
        print(f"[{device_code}:{label}] 리플레이할 데이터가 없습니다")
        return

    print(f"[{device_code}:{label}] 리플레이 시작 — {len(batches)}행 / {interval}초 간격")
    success = 0
    for i, (source_seq, measurements) in enumerate(batches, start=1):
        if send(base_url, device_code, source_seq, measurements):
            success += 1
        if i % 20 == 0 or i == len(batches):
            print(f"  [{device_code}] {i}/{len(batches)} 전송 (마지막 sourceSeq={source_seq})")
        if i < len(batches):
            time.sleep(interval)

    print(f"[{device_code}:{label}] 완료 — {success}/{len(batches)}건 성공")


# =============================================================================
# 4. main
#
# 사용 예시:
#   bash services/simulator/data/download.sh                     # 데이터 먼저 내려받기
#   python services/simulator/simulator.py --all                 # 물리 device 3개 전체 리플레이
#   python services/simulator/simulator.py --devices CMAPSS-U1 CNC-EXP01 --interval 0.5 --limit 100
#   python services/simulator/simulator.py --all --base-url http://localhost:23100
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="IoT 센서 시뮬레이터 — 실측 데이터 리플레이",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--devices", nargs="+", type=str, metavar="CODE",
                        help="리플레이할 device code 목록 (예: CMAPSS-U1 CNC-EXP01, 기본: --all)")
    parser.add_argument("--all", action="store_true", help="전체 물리 device 3개 리플레이")
    parser.add_argument("--interval", type=float, default=1.0, help="행 간 간격 초 (기본 1.0)")
    parser.add_argument("--limit", type=int, default=0, help="장치당 최대 행 수 (기본 0=전체)")
    parser.add_argument("--base-url", type=str, default="http://localhost:23100", help="서버 주소")
    args = parser.parse_args()

    if not args.all and not args.devices:
        parser.error("--devices 또는 --all 중 하나를 지정하세요.")

    if args.all:
        presets = list(REPLAY_PRESET)
    else:
        by_code = {p["code"]: p for p in REPLAY_PRESET}
        presets = []
        for code in args.devices:
            if code not in by_code:
                parser.error(f"알 수 없는 device code: {code} (사용 가능: {sorted(by_code)})")
            presets.append(by_code[code])

    print(f"시뮬레이터 시작 — device {len(presets)}개 / {args.interval}초 간격 / 서버 {args.base_url}")
    print("=" * 50)

    targets = [(p, args.interval, args.limit, args.base_url) for p in presets]
    with Pool(processes=len(targets)) as pool:
        pool.starmap(worker, targets)

    print("=" * 50)
    print("전체 완료")


if __name__ == "__main__":
    main()
