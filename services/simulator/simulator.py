"""
IoT Sensor Simulator — 실측 데이터 리플레이 + 합성 데이터 스트림

replay 모드는 저장된 공개 센서 시계열(services/simulator/data/)을 시간 순으로
흘려보내고, synthetic 모드는 정상 변동·간헐적 이상·회복 패턴을 계속 생성한다.
두 모드 모두 POST /sensor-data 로 원본 한 행 또는 생성 시점 한 번을 batch 로 전송한다.

데이터는 먼저 내려받아야 한다:  bash services/simulator/data/download.sh

수신 모델: 물리 Device 아래 여러 SensorChannel 이 있고, 원본 데이터의 한 행이
관측 한 묶음(batch)이 된다 — 원본 1행 = measurements map 1건 = POST 1건.
장치(deviceCode)·채널(channel code) 매핑은 services/simulator/seed.sql 의
device/sensor_channel 삽입 내용과 일치해야 한다.
"""

import argparse
import csv
import os
import random
import signal
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, time as clock_time
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

PRINT_LOCK = threading.Lock()


def log(message: str):
    """여러 device thread의 한 줄 로그가 서로 섞이지 않게 출력한다."""
    with PRINT_LOCK:
        print(message, flush=True)


try:
    import requests
except ImportError:
    log("[오류] requests 라이브러리가 필요합니다: pip install requests")
    sys.exit(1)


def handle_stop_signal(_signum, _frame):
    """컨테이너 SIGTERM도 로컬 Ctrl+C와 같은 정리 경로로 보낸다."""
    raise KeyboardInterrupt


signal.signal(signal.SIGTERM, handle_stop_signal)


DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
INGEST_KEY_ENV = "INGEST_API_KEY"

# C-MAPSS train_FD001: 공백 구분, 헤더 없음. 컬럼 인덱스 = unit0, cycle1, set2~4, s1=5 ... s21=25
# 대표 채널: s2/s4/s7/s11/s15/s21 = index 6/8/11/15/19/25
# CNC 실험 CSV: 헤더 있음(DictReader), 채널 code = 원본 컬럼명 그대로 사용.
DEVICE_PRESETS = [
    {
        "code": "CMAPSS-U1",
        "kind": "cmapss",
        "unit": 1,
        "channels": {"s2": 6, "s4": 8, "s7": 11, "s11": 15, "s15": 19, "s21": 25},
        "synthetic": {
            "s2": {"normal": 642.4, "noise": 0.06, "anomaly": 644.1},
            "s4": {"normal": 1404.0, "noise": 0.8, "anomaly": 1424.0},
            "s7": {"normal": 554.0, "noise": 0.08, "anomaly": 551.8},
            "s11": {"normal": 47.2, "noise": 0.04, "anomaly": 48.3},
            "s15": {"normal": 8.42, "noise": 0.006, "anomaly": 8.54},
            "s21": {"normal": 23.36, "noise": 0.02, "anomaly": 23.0},
        },
        "label": "엔진 유닛1",
    },
    {
        "code": "CMAPSS-U2",
        "kind": "cmapss",
        "unit": 2,
        "channels": {"s2": 6, "s4": 8, "s7": 11, "s11": 15, "s15": 19, "s21": 25},
        "synthetic": {
            "s2": {"normal": 642.5, "noise": 0.06, "anomaly": 644.0},
            "s4": {"normal": 1406.0, "noise": 0.9, "anomaly": 1422.0},
            "s7": {"normal": 553.9, "noise": 0.08, "anomaly": 551.9},
            "s11": {"normal": 47.1, "noise": 0.05, "anomaly": 48.2},
            "s15": {"normal": 8.42, "noise": 0.006, "anomaly": 8.53},
            "s21": {"normal": 23.35, "noise": 0.02, "anomaly": 23.0},
        },
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
            "Y1_ActualAcceleration": "Y1_ActualAcceleration",
            "Z1_ActualAcceleration": "Z1_ActualAcceleration",
            "X1_CurrentFeedback": "X1_CurrentFeedback",
            "S1_ActualVelocity": "S1_ActualVelocity",
            "M1_CURRENT_FEEDRATE": "M1_CURRENT_FEEDRATE",
        },
        "synthetic": {
            "S1_OutputPower": {"normal": 0.12, "noise": 0.01, "anomaly": 0.36},
            "S1_CurrentFeedback": {"normal": 14.0, "noise": 0.8, "anomaly": 38.0},
            "X1_ActualAcceleration": {"normal": 180.0, "noise": 35.0, "anomaly": -1050.0},
            "Y1_ActualAcceleration": {"normal": 120.0, "noise": 24.0, "anomaly": 700.0},
            "Z1_ActualAcceleration": {"normal": 160.0, "noise": 42.0, "anomaly": -1300.0},
            "X1_CurrentFeedback": {"normal": 4.5, "noise": 0.7, "anomaly": -19.0},
            # 표시 전용 채널은 이상 구간에도 정상 운전 중심값을 유지한다.
            "S1_ActualVelocity": {"normal": 53.3, "noise": 0.08, "anomaly": 53.3},
            "M1_CURRENT_FEEDRATE": {"normal": 50.0, "noise": 0.05, "anomaly": 50.0},
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
            measurements = {}
            for name, col in channels.items():
                try:
                    measurements[name] = float(row[col])
                except (KeyError, ValueError):
                    continue
            if not measurements:
                continue
            batches.append((idx, measurements))
    return batches


def load_batches(preset: dict) -> list[tuple[int, dict]]:
    if preset["kind"] == "cmapss":
        return load_cmapss_batches(preset["unit"], preset["channels"])
    return load_cnc_batches(preset["file"], preset["channels"])


# =============================================================================
# 2. 합성 데이터 생성과 운영시간 판정
# =============================================================================

WEEKDAYS = {
    "mon": 0,
    "tue": 1,
    "wed": 2,
    "thu": 3,
    "fri": 4,
    "sat": 5,
    "sun": 6,
}


def parse_active_days(value: str) -> set[int]:
    """mon-fri, mon,wed,fri, all 형식을 weekday 번호 집합으로 바꾼다."""
    value = value.strip().lower()
    if value == "all":
        return set(range(7))

    result = set()
    for part in value.split(","):
        part = part.strip()
        if "-" not in part:
            if part not in WEEKDAYS:
                raise ValueError(f"알 수 없는 요일: {part}")
            result.add(WEEKDAYS[part])
            continue

        start_name, end_name = (item.strip() for item in part.split("-", 1))
        if start_name not in WEEKDAYS or end_name not in WEEKDAYS:
            raise ValueError(f"알 수 없는 요일 범위: {part}")
        current = WEEKDAYS[start_name]
        end = WEEKDAYS[end_name]
        result.add(current)
        while current != end:
            current = (current + 1) % 7
            result.add(current)

    if not result:
        raise ValueError("운영 요일이 비어 있습니다")
    return result


def parse_active_hours(value: str | None) -> tuple[clock_time, clock_time] | None:
    """HH:MM-HH:MM 형식을 파싱한다. None 이면 하루 종일 활성이다."""
    if value is None:
        return None
    try:
        start_text, end_text = (item.strip() for item in value.split("-", 1))
        start = clock_time.fromisoformat(start_text)
        end = clock_time.fromisoformat(end_text)
    except (TypeError, ValueError) as exc:
        raise ValueError("운영시간은 HH:MM-HH:MM 형식이어야 합니다") from exc
    if start == end:
        raise ValueError("운영 시작과 종료 시각은 달라야 합니다")
    return start, end


def is_active_at(now: datetime, active_days: set[int], active_hours: tuple[clock_time, clock_time] | None) -> bool:
    """운영시간인지 판정한다. 자정을 넘는 교대는 시작 요일에 속한다."""
    if active_hours is None:
        return now.weekday() in active_days

    start, end = active_hours
    current = now.timetz().replace(tzinfo=None)
    if start < end:
        return now.weekday() in active_days and start <= current < end
    if current >= start:
        return now.weekday() in active_days
    previous_weekday = (now.weekday() - 1) % 7
    return previous_weekday in active_days and current < end


class SyntheticGenerator:
    """정상 랜덤워크에 짧은 이상 구간을 섞고 다시 정상값으로 회복시킨다."""

    def __init__(self, channel_specs: dict, seed: int | None, anomaly_rate: float):
        self.rng = random.Random(seed)
        self.channel_specs = channel_specs
        self.values = {name: spec["normal"] for name, spec in channel_specs.items()}
        self.anomaly_rate = anomaly_rate
        self.anomaly_remaining = 0

    def next_measurements(self) -> dict[str, float]:
        if self.anomaly_remaining == 0 and self.rng.random() < self.anomaly_rate:
            self.anomaly_remaining = self.rng.randint(3, 8)

        anomalous = self.anomaly_remaining > 0
        measurements = {}
        for name, spec in self.channel_specs.items():
            target = spec["anomaly"] if anomalous else spec["normal"]
            current = self.values[name]
            current += (target - current) * 0.35 + self.rng.gauss(0.0, spec["noise"])
            self.values[name] = current
            measurements[name] = round(current, 4)

        if anomalous:
            self.anomaly_remaining -= 1
        return measurements

    def force_anomaly(self, ticks: int = 8):
        """데모용: 확률 롤과 무관하게 이상 구간을 즉시 시작한다(임계 확실히 넘도록 길게)."""
        self.anomaly_remaining = ticks


# =============================================================================
# 3. HTTP 전송
# =============================================================================

def require_ingest_api_key(environ: dict | None = None) -> str:
    """수신 키가 없거나 공백이면 HTTP 작업을 시작하기 전에 종료한다."""
    source = os.environ if environ is None else environ
    ingest_api_key = source.get(INGEST_KEY_ENV)
    if ingest_api_key is None or not ingest_api_key.strip():
        raise ValueError(f"{INGEST_KEY_ENV} 환경변수가 필요합니다")
    return ingest_api_key.strip()


def send(base_url: str, device_code: str, source_seq: int, measurements: dict, ingest_api_key: str) -> bool:
    payload = {
        "deviceCode": device_code,
        "sourceSeq": source_seq,
        "measurements": measurements,
    }
    try:
        response = requests.post(
            f"{base_url}/sensor-data",
            json=payload,
            headers={"X-Ingest-Key": ingest_api_key},
            timeout=5,
        )
    except requests.exceptions.ConnectionError:
        log(f"  [{device_code}] [오류] 서버 연결 실패: {base_url}")
        return False
    except requests.exceptions.Timeout:
        log(f"  [{device_code}] [오류] 요청 타임아웃")
        return False

    # 404(deviceCode 미존재) / 422(전 채널 미지) / 400(validation) 모두 응답 본문을 로그로
    # 남기고 다음 행으로 넘어간다 — 같은 행에 대한 재시도는 하지 않는다.
    if response.status_code >= 400:
        log(f"  [{device_code}] [오류] HTTP {response.status_code}: {response.text}")
        return False
    return True


# =============================================================================
# 4. 워커 (스레드에서 물리 device 별로 실행)
# =============================================================================

def replay_worker(
        preset: dict,
        interval: float,
        limit: int,
        base_url: str,
        ingest_api_key: str,
        stop_event: threading.Event,
):
    device_code = preset["code"]
    label = preset.get("label", device_code)

    try:
        batches = load_batches(preset)
    except FileNotFoundError:
        log(f"[{device_code}:{label}] 데이터 파일 없음 — 먼저 'bash services/simulator/data/download.sh' 실행")
        return

    if limit > 0:
        batches = batches[:limit]
    if not batches:
        log(f"[{device_code}:{label}] 리플레이할 데이터가 없습니다")
        return

    log(f"[{device_code}:{label}] 리플레이 시작 — {len(batches)}행 / {interval}초 간격")
    success = 0
    attempted = 0
    for i, (source_seq, measurements) in enumerate(batches, start=1):
        if stop_event.is_set():
            break
        attempted += 1
        if send(base_url, device_code, source_seq, measurements, ingest_api_key):
            success += 1
        if i % 20 == 0 or i == len(batches):
            log(f"  [{device_code}] {i}/{len(batches)} 전송 (마지막 sourceSeq={source_seq})")
        if i < len(batches) and stop_event.wait(interval):
            break

    log(f"[{device_code}:{label}] 완료 — {success}/{attempted}건 성공")


def synthetic_worker(
        preset: dict,
        interval: float,
        limit: int,
        base_url: str,
        ingest_api_key: str,
        seed: int | None,
        anomaly_rate: float,
        active_days: set[int],
        active_hours: tuple[clock_time, clock_time] | None,
        timezone_name: str,
        first_anomaly_after: float | None,
        stop_event: threading.Event,
):
    device_code = preset["code"]
    label = preset.get("label", device_code)
    timezone = ZoneInfo(timezone_name)
    generator = SyntheticGenerator(preset["synthetic"], seed, anomaly_rate)
    source_seq = int(time.time())
    # 데모용 보장 이상: 전송 시도 기준 tick으로 환산해 한 번만 발화한다(벽시계 대신 결정적).
    forced_tick = None if first_anomaly_after is None else max(1, round(first_anomaly_after / interval))
    forced_done = False
    attempted = 0
    success = 0
    failure_streak = 0
    waiting = False
    limit_label = str(limit) if limit > 0 else "무제한"
    log(f"[{device_code}:{label}] 합성 스트림 시작 — {limit_label} / {interval}초 간격")

    try:
        while not stop_event.is_set() and (limit == 0 or attempted < limit):
            now = datetime.now(timezone)
            if not is_active_at(now, active_days, active_hours):
                if not waiting:
                    log(f"  [{device_code}] 운영시간 밖 — 전송 대기")
                    waiting = True
                stop_event.wait(min(30.0, max(interval, 1.0)))
                continue
            if waiting:
                log(f"  [{device_code}] 운영시간 시작 — 전송 재개")
                waiting = False

            attempted += 1
            if forced_tick is not None and not forced_done and attempted >= forced_tick:
                generator.force_anomaly()
                forced_done = True
            measurements = generator.next_measurements()
            if send(base_url, device_code, source_seq, measurements, ingest_api_key):
                success += 1
                failure_streak = 0
            else:
                failure_streak += 1
            source_seq += 1

            if attempted % 20 == 0 or (limit > 0 and attempted == limit):
                log(f"  [{device_code}] {attempted}/{limit_label} 전송 — 성공 {success}")

            if limit > 0 and attempted >= limit:
                break

            sleep_seconds = interval if failure_streak == 0 else min(60.0, max(interval, 1.0) * (2 ** min(failure_streak, 6)))
            stop_event.wait(sleep_seconds)
    finally:
        log(f"[{device_code}:{label}] 종료 — {success}/{attempted}건 성공")


# =============================================================================
# 5. main
#
# 사용 예시:
#   bash services/simulator/data/download.sh                     # 데이터 먼저 내려받기
#   python services/simulator/simulator.py --mode replay --all
#   python services/simulator/simulator.py --mode synthetic --all --interval 10
#   python services/simulator/simulator.py --mode synthetic --all --active-days mon-fri --active-hours 08:00-18:00
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="IoT 센서 시뮬레이터 — 실측 리플레이 또는 합성 스트림",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--mode", choices=("replay", "synthetic"), default="replay",
                        help="실행 모드 (기본 replay)")
    parser.add_argument("--devices", nargs="+", type=str, metavar="CODE",
                        help="대상 device code 목록 (예: CMAPSS-U1 CNC-EXP01, 기본: --all)")
    parser.add_argument("--all", action="store_true", help="전체 물리 device 3개 선택")
    parser.add_argument("--interval", type=float, default=None,
                        help="전송 간격 초 (기본 replay=1, synthetic=10)")
    parser.add_argument("--limit", type=int, default=0,
                        help="장치당 최대 전송 수 (기본 0: replay=전체, synthetic=무제한)")
    parser.add_argument("--base-url", type=str, default="http://localhost:23100", help="서버 주소")
    parser.add_argument("--seed", type=int, default=None,
                        help="synthetic 난수 seed (생략 시 실행마다 다른 패턴)")
    parser.add_argument("--anomaly-rate", type=float, default=0.02,
                        help="synthetic에서 이상 구간을 시작할 확률 (기본 0.02)")
    parser.add_argument("--first-anomaly-after", type=float, default=None,
                        help="synthetic에서 부팅 후 N초에 이상 1회 보장 (데모용, 생략 시 off)")
    parser.add_argument("--active-days", default="all",
                        help="synthetic 운영 요일 (all, mon-fri, mon,wed,fri)")
    parser.add_argument("--active-hours", default=None,
                        help="synthetic 운영시간 (예: 08:00-18:00, 생략 시 하루 종일)")
    parser.add_argument("--timezone", default="Asia/Seoul",
                        help="운영시간 IANA timezone (기본 Asia/Seoul)")
    args = parser.parse_args()

    if args.interval is None:
        args.interval = 1.0 if args.mode == "replay" else 10.0
    if args.interval <= 0:
        parser.error("--interval은 0보다 커야 합니다")
    if args.limit < 0:
        parser.error("--limit은 0 이상이어야 합니다")
    if not 0.0 <= args.anomaly_rate <= 1.0:
        parser.error("--anomaly-rate는 0 이상 1 이하여야 합니다")
    if args.first_anomaly_after is not None and args.first_anomaly_after <= 0:
        parser.error("--first-anomaly-after는 0보다 커야 합니다")

    active_days = set(range(7))
    active_hours = None
    if args.mode == "synthetic":
        try:
            active_days = parse_active_days(args.active_days)
            active_hours = parse_active_hours(args.active_hours)
            ZoneInfo(args.timezone)
        except (ValueError, ZoneInfoNotFoundError) as exc:
            parser.error(str(exc))

    if not args.all and not args.devices:
        parser.error("--devices 또는 --all 중 하나를 지정하세요.")

    if args.all:
        presets = list(DEVICE_PRESETS)
    else:
        by_code = {p["code"]: p for p in DEVICE_PRESETS}
        presets = []
        for code in args.devices:
            if code not in by_code:
                parser.error(f"알 수 없는 device code: {code} (사용 가능: {sorted(by_code)})")
            presets.append(by_code[code])

    try:
        ingest_api_key = require_ingest_api_key()
    except ValueError as exc:
        parser.error(str(exc))

    schedule = (f"{args.active_days} 하루 종일 {args.timezone}" if active_hours is None
                else f"{args.active_days} {args.active_hours} {args.timezone}")
    log(f"시뮬레이터 시작 — mode {args.mode} / device {len(presets)}개 / {args.interval}초 간격 / 서버 {args.base_url}")
    if args.mode == "synthetic":
        log(f"운영시간 {schedule} / seed {args.seed if args.seed is not None else 'random'}")
        if args.first_anomaly_after is not None:
            log(f"데모: 부팅 후 {args.first_anomaly_after:g}초에 이상 1회 보장")
    log("=" * 50)
    stop_event = threading.Event()

    if args.mode == "replay":
        targets = [(p, args.interval, args.limit, args.base_url, ingest_api_key, stop_event) for p in presets]
        worker_function = replay_worker
    else:
        targets = [
            (p, args.interval, args.limit, args.base_url, ingest_api_key,
             None if args.seed is None else args.seed + index,
             args.anomaly_rate, active_days, active_hours, args.timezone,
             args.first_anomaly_after, stop_event)
            for index, p in enumerate(presets)
        ]
        worker_function = synthetic_worker

    with ThreadPoolExecutor(max_workers=len(targets)) as executor:
        futures = [executor.submit(worker_function, *target) for target in targets]
        try:
            for future in futures:
                future.result()
        except KeyboardInterrupt:
            log("\n종료 요청 수신 — worker를 정리합니다")
            stop_event.set()

    log("=" * 50)
    log("전체 완료")


if __name__ == "__main__":
    main()
