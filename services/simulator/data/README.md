# 센서 데이터 (실측 공개 데이터)

시뮬레이터가 리플레이할 실측 센서 시계열을 여기에 둔다. 데이터 파일은 무인증 공개 소스에서 내려받아 사용하며, 저장소에는 커밋하지 않는다(`.gitignore`).

## 다운로드

```bash
bash services/simulator/data/download.sh
```

## 출처

| 파일 | 데이터 | 출처 |
|---|---|---|
| `cmapss_train_FD001.txt` | NASA C-MAPSS 터보팬 열화 시뮬레이션 (엔진 100대, 사이클별 센서) | https://raw.githubusercontent.com/hankroark/Turbofan-Engine-Degradation/master/CMAPSSData/train_FD001.txt |
| `cnc_experiment_01~03.csv` | CNC 밀링 가공 센서 (100ms 샘플링, UMich SMART Lab) | https://github.com/SaeedShurrab/Tool-Wear-Detection-in-CNC-Milling-Operartions |
| `cnc_train.csv` | 위 실험별 라벨(마모 여부 등) | 위와 동일 |

## 컬럼 개요

- C-MAPSS: 공백 구분, 헤더 없음, 26컬럼 = `unit, cycle, setting1~3, s1~s21`. 리플레이에 쓰는 채널은 `s4`(온도 계열), `s11`(압력 계열).
- CNC: 헤더 있는 CSV, 축별(X1/Y1/Z1/S1) 채널 다수. 리플레이에 쓰는 채널은 `S1_OutputPower`, `S1_CurrentFeedback`, `X1_ActualAcceleration` 등.

각 데이터의 재배포 조건은 원 출처 페이지에서 확인한다.
