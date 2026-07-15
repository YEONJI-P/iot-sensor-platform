#!/usr/bin/env bash
# 공개 센서 시계열 데이터를 services/simulator/data/ 로 내려받는다. 저장소에는 커밋하지 않는다(.gitignore).
# 시뮬레이터가 이 CSV들을 시간 순으로 리플레이해 실시간 수신을 재현한다.
set -euo pipefail
cd "$(dirname "$0")"

# C-MAPSS 터보팬 열화 시뮬레이션 (NASA) — 공백 구분, 헤더 없음, 26컬럼
#   컬럼: unit, cycle, setting1~3, s1~s21
CMAPSS_URL="https://raw.githubusercontent.com/hankroark/Turbofan-Engine-Degradation/master/CMAPSSData/train_FD001.txt"
curl -fsSL "$CMAPSS_URL" -o cmapss_train_FD001.txt
echo "받음: cmapss_train_FD001.txt"

# KAMP CNC 밀링 가공 (UMich SMART Lab) — 헤더 있는 CSV, 100ms 샘플링
CNC_BASE="https://raw.githubusercontent.com/SaeedShurrab/Tool-Wear-Detection-in-CNC-Milling-Operartions/master/tool-wear-detection-in-cnc-mill"
for exp in 01 02 03; do
  curl -fsSL "$CNC_BASE/experiment_${exp}.csv" -o "cnc_experiment_${exp}.csv"
  echo "받음: cnc_experiment_${exp}.csv"
done
curl -fsSL "$CNC_BASE/train.csv" -o cnc_train.csv
echo "받음: cnc_train.csv (실험별 라벨)"

echo "완료 — services/simulator/data/ 에 저장됨"
