# GNSS Benchmark requirements

## Phase 2 — 실시간 분석 추가 (2026-10-05)
Phase 1 baseline을 유지하며 Static Benchmark 화면에 실시간 cumulative CEP95(cm/m), mean/median/RMSE/MAX, 최근 60초 rolling CEP95, 시간별 actual error/reported accuracy/rolling CEP95 그래프, GT 중심 동서/남북 산점도를 구현한다. warm-up 표본은 모든 분석에서 제외한다. 대시보드는 측정 시작 후 자동 노출하고 완료 후 같은 결과를 유지한다.

CEP95는 관측 오차의 R7 percentile이지 95% 신뢰구간이나 미래 보장값이 아니다. cm 표시는 단위 변환이며 cm급 성능을 암시하지 않는다. GT 측량 방법과 선택적인 불확실성(m)을 기록하고 미입력은 미확인으로 표시한다. GT 불확실성을 실제 오차에 임의 가감하지 않는다. 누적 표본 수/마지막 fix 나이/1초 구간 수신 비율/최대 무수신 간격을 함께 표시한다. 수신 비율은 fix가 한 개 이상인 1초 bin / 경과한 측정 bin이며 독립 표본 수·정확도 신뢰도가 아니다. 정상 30분 이상 완료와 부분 측정/오류/준비 중을 구분하되 통계적으로 검증된 신뢰 등급을 만들어내지 않는다.

저장 세션 재열람·선택 export와 타 기기 JSON 결과 불러오기/비교를 지원한다. 원본 CSV/JSON을 유지하고 분석 정의 및 summary를 추가한다. 그래프는 앱의 Compose Canvas로 그리며 통계는 pure Kotlin에서 계산한다. rolling window (t-60s,t], 첫 60초는 가용 구간; gap에서 line을 끊고 warm-up/no-fix에는 통계를 만들지 않는다. 거리 구면 근사 한계와 시간 상관 때문에 표본 수만으로 cm 정확도를 보장할 수 없음을 설명한다. 실제 30분 benchmark는 기본 warm-up 포함 총 35분이다.

## 1. Project Goal
Zebra TC58 / TC501에 **동일 APK**를 설치해 독립 측정한 데이터를 export하여 GNSS 성능 및 향후 오프라인 보정 효과를 정량 비교한다. 단순 지도 앱이 아니다. Ground Truth(GT) 기준 실제 오차, 장시간 안정성, classical/IMU/ML/hybrid 개선량, CPU/GPU/NPU 실행 비용을 재현 가능한 데이터로 평가한다. Phase 1 수집 기반 위에 Phase 2 실시간 분석을 구현한다.

## 2. Target Devices
TC58 및 TC501. 제조사·모델·OS·API·Build hardware·GNSS model/year는 public API runtime 값만 표시한다. 칩셋, 지원 band, 정밀도를 모델명으로 추정하거나 hard-code하지 않는다. DETECTED = API 관측; SPECIFICATION = 출처가 있는 제조사 자료(Phase 1 자동 조회 없음); DERIVED = 관측으로 계산한 값. 관측하지 못함은 미지원 증거가 아니다.

## 3. Measurement Philosophy
- Reported Accuracy: Android Location.accuracy, m. Android가 보고하는 수평 불확실성이지 실제 오차가 아니다.
- Measured Error: 유효한 GT와 GPS_PROVIDER 위치의 수평 거리, m. GT가 없으면 계산/표시하지 않는다.
- Derived Statistics: 해당 세션의 실제 오차 표본에서 계산. 제조사 보장이나 다른 환경의 정확도를 의미하지 않는다.
- P0 Raw GNSS는 Android GNSS 위치해를 보정 없이 보존한다는 의미다. 원시 pseudorange로 앱이 독자적으로 위치해를 계산한다는 의미가 아니다.
- 원본 위치/위성/원시 관측을 보존한다. 보정 출력은 원본을 덮어쓰지 않는다. mock location을 실제 benchmark 표본으로 사용하지 않는다.

## 4. Standard Static Benchmark Protocol
Outdoor open sky, 기기 정지, 사전에 독립적으로 확보한 WGS84 GT. 기본 warm-up 5분 + measurement **30분**. 선택 측정시간 5/15/30/60분. debug 빌드만 warm-up skip 허용하고 metadata에 기록한다. warm-up 표본은 보존하되 통계에서 제외한다. 측정 구간은 monotonic time의 [시작, 끝)이며 위치 fix 유무와 무관하게 시간이 진행한다. 두 기기는 독립적으로 동일 장소/가능한 동일 조건, 같은 APK/설정/설치 자세로 측정한다. GT 측량 방법과 불확실성, 환경/시간/기기 배치를 실험 노트에 기록한다. GNSS fix가 없는 시간 및 중단 세션을 숨기지 않는다.

## 5. Metrics
Phase 1: Mean, Median, RMSE, CEP50/68/95/99, MAX (m); within 1/2/3/5/10m (%); Android reported horizontal accuracy (m); satellite count/used in fix; mean/strongest C/N0 (dB-Hz). Phase 2: rolling CEP95, 시간별 안정성 및 세션 비교.

수평 거리: WGS84 위경도에 mean Earth radius 6,371,008.8m haversine approximation; 고도 제외. 구면 근사이며 정밀 측량용 타원체 역해와 차이가 있다. percentile: 정렬한 n 표본에 h=(n-1)p, 인접 순위 선형 보간 (R7). CEP는 GT 중심 방사 오차의 empirical percentile. 평균 위치 중심의 precision과 구분한다. 빈 집합은 null, 한 표본의 모든 percentile은 그 값. RMSE=sqrt(mean(error²)); within은 <= 임계값. 필터링하지 않은 유효 측정 표본 전체가 분모다.

## 6. Future Correction Pipeline
P0 Raw → P1 outlier/jump rejection → P2 EMA/Kalman → P3 C/N0, constellation, satellite state, observed frequency 기반 quality correction → P4 accelerometer/gyro 및 필요한 magnetometer GNSS fusion → P5 최근 N초 GNSS/quality/IMU 시계열의 position error/correction vector LiteRT 추론 → P6 classical/fusion/ML hybrid. 동일 raw replay, 시간 정렬, 공통 GT/프로토콜로 A/B 평가한다. GT는 평가 라벨이며 추론 입력으로 유출하지 않는다.

## 7. Future Edge AI Evaluation
동일 model/hash, 입력, 전후처리, precision 설정을 기록하고 가능한 CPU/GPU/NPU backend를 비교한다. 요청 backend와 실제 실행 backend/fallback을 구분한다. latency P50/P95, CPU, memory, thermal state, power/energy proxy를 측정한다. NPU는 모델 실행 backend이며 GNSS 정확도를 직접 높이지 않는다. 정확도 개선과 accelerator 효율은 별도 축이다.

## 8. Architecture Principles
Single Activity / Jetpack Compose. ui, gnss, analysis, recording, model 및 session 패키지. GNSS는 Android adapter, 계산/상태 전이는 pure Kotlin. ViewModel은 service 연결/UI flow orchestration. 통계는 UI 밖에 둔다. background 60분 측정을 위해 location foreground service가 수집/세션을 소유한다. 불필요한 repository/use-case 계층이나 현재 사용하지 않는 PositionCorrector 추상화는 만들지 않는다. 후속 filtering, sensorfusion, inference를 별도로 추가한다. pure Kotlin은 향후 Compose Multiplatform에서 재사용한다.

## 9. Development Phases
1. Device Information + Raw Static GNSS Benchmark: runtime 정보, precise permission, 5분 warm-up, 5/15/30/60분, 상태 머신, GT 거리/통계, 결과, CSV/JSON SAF export, raw measurement 수집 기반, local persistence, tests/build/lint.
2. **현재** Visualization + Robust Statistics: 실시간 cm 단위 CEP95, error chart, scatter, rolling CEP95, 수신 공백, 저장 세션 재열람 및 TC58/TC501 JSON export 비교.
3. Classical Correction: outlier, EMA, Kalman, raw/corrected A/B.
4. GNSS Quality Features: C/N0/constellation/frequency/satellite-aware correction.
5. IMU Sensor Fusion: accelerometer/gyro/GNSS, stationary/moving detection.
6. Dataset + ML: training export, Python training, correction model, LiteRT-compatible generation.
7. LiteRT Edge Inference: CPU/GPU/NPU 실제 backend와 latency/resource 비교.
8. Final Benchmark: 두 기기 각각 Raw/Classical/Fusion/ML/Hybrid 동일 프로토콜 비교.

## 10. Phase 1 acceptance
- 상단 PREV/NEXT; Device Info / Static Benchmark / Results. Device 정보와 runtime 관측을 출처별 표시. 관측 주파수는 지원 대역 전체로 해석하지 않는다.
- GT latitude [-90,90], longitude [-180,180], finite; precise permission 및 provider 없으면 시작 금지. 거부/위치 꺼짐/수집 오류 안내. 필수 permission만 요청, Maps/key/network/storage broad permission 없음.
- Idle → Warming Up → Measuring → Completed 또는 Error. Stop은 partial 완료, Reset은 UI 새 세션 준비이며 저장 기록은 유지. 화면회전과 background에서 수집 유지. 프로세스 강제 종료시 자동 이어붙이지 않고 interrupted 기록으로 복구한다.
- sample에 wall clock/elapsed realtime, lat/lon, optional altitude/accuracy/speed/bearing, actual error, satellite snapshot 연결을 보존. raw event에 clock 및 measurement constellation/frequency/CN0/pseudorange rate/ADR 상태·단위를 보존.
- measurement timing, received timestamp, stale/duplicate/mock 거부 수를 기록해 replay와 audit 가능. raw callback 미지원/미관측은 위치 수집 실패와 구분.
- CSV와 JSON에 metadata, samples, summary, 별도 원시 관측을 포함; SAF destination 사용자 선택. 결과는 CEP95 및 해당 세션에서만 유효함을 강조.
- pure Kotlin tests: distance, GT invalid, percentile/CEP50/95, RMSE, within, empty/single/multiple, state transitions 및 경계.
- 실제 assembleDebug, testDebugUnitTest, lintDebug 성공이 소프트웨어 완료 조건. 물리 기기 없을 때 실제 GNSS 정확도/60분 background/하드웨어 backend 검증은 미검증으로 명시한다.
