# Phase 2 validation — 2026-10-05 (Asia/Seoul)

## 현재 산출물: 1.1-phase2

- `gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon`: **BUILD SUCCESSFUL**.
- 총 **28 tests**, failures 0 / errors 0: AnalysisTest 12, LiveAnalysisTest 8, RecordingTest 4, SessionReplayTest 4.
- Lint: **0 errors / 4 warnings**. 기존 고정 Compose BOM/activity/lifecycle/core 최신 버전 안내만 남았습니다.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, versionCode 2, versionName 1.1-phase2, 20,777,063 bytes.
- SHA256: `B5F7CA850C8F64184BDE97843D25B291C3E223D23DF6D23FC742583FD93164B5`.

추가 테스트: rolling 60초의 경계/무수신 시 만료, 다중 fix가 있는 1초 bin coverage, 시작/종료 무수신 간격, warm-up 제외, 산점도 동북 방향/날짜변경선/거리 보존, 그래프 spike/gap 보존, 30분 분량 합성 시계열, partial stop의 시간 고정, snapshot 불변성, invalid GT uncertainty, import 통계 재계산, 중복 timestamp 거부, 중단/손상 기록 표시.

## 에뮬레이터 UI / 서비스 smoke test

연결된 실기기가 없어 기존 Automotive Android 15 AVD를 **read-only / no-snapshot / no-window** 모드로 별도 실행했습니다. 세로 해상도 480×960에서 APK 설치·cold start 성공, Device Info / Sessions / Compare / cm CEP95 카드 / 3계열 시간 그래프 / GT 산점도를 확인했습니다. 큰 글꼴에서도 내용은 스크롤 가능하고 차트 축·범례를 확인했습니다. 합성 replay fixture는 `QA SYNTHETIC / NOT A DEVICE MEASUREMENT`라고 명시하고 앱 소스/APK에 포함하지 않았습니다.

정확한 위치 권한 허용 → 테스트 GT 입력 → Start → Measuring 전환과 경과 시간 갱신을 확인했습니다. `dumpsys activity services`에서 location foreground type과 `isForeground=true` 확인. 약 58초 후 Stop → Completed / 부분 측정 문구와 결과 유지, 서비스 종료(`nothing`) 확인. 에뮬레이터의 가상 위치와 임의 테스트 GT에서 나온 수치는 실기기 GNSS 성능 결과가 아닙니다.

앱 프로세스의 crash는 관측되지 않았습니다. 해당 Automotive 이미지 자체의 CarPowerManagementService와 screencap 프로세스 오류는 있어 이 환경은 TC58/TC501 대체 검증이 아닙니다. 확인 후 임시 에뮬레이터를 종료했습니다.

## 남은 실기기 검증

TC58/TC501 각각의 실제 위치·위성/raw observation 수신, 기본 **warm-up 5분 + 측정 30분** 전체 완료, 60분 측정, 회전·Home·화면 꺼짐 수집 유지, 각 기기의 SAF provider에서 CSV/JSON 저장 및 타 기기 JSON 가져오기. 자동 테스트의 합성 30분 replay는 실제로 30분 동안 실기기를 실행한 테스트가 아닙니다. NPU/ML/IMU 보정은 구현하지 않았습니다.

보고서: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`. UI 확인 스크린샷은 로컬 `app/build/gnss-chart-qa.png`, `app/build/gnss-scatter-qa.png`, `app/build/gnss-live-qa.png`에 있으며 모두 테스트/에뮬레이터 자료입니다. build 폴더는 Git에 포함하지 않습니다.

---

# 이전 Phase 1 검증 기록 — 2026-10-05 (아래 APK hash는 이전 버전)

## 실제 실행 결과

Windows에서 Android Studio JBR 21.0.10, Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21, Android SDK/Build Tools 34로 실행했습니다.

```text
gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon
BUILD SUCCESSFUL
```

- `assembleDebug`: 성공. 설치 가능한 debug APK 생성.
- `testDebugUnitTest`: 총 16개 성공, failures 0, errors 0. AnalysisTest 12개 + RecordingTest 4개.
- `lintDebug`: 성공, **0 errors / 4 warnings**. 네 경고는 Compose BOM/activity/lifecycle/core의 최신 버전 안내(GradleDependency)입니다. SDK 34/AGP 8.5.2 호환성을 위해 고정한 버전이며 suppression으로 숨기지 않았습니다. 앱 코드/manifest 관련 경고는 수정했습니다.
- APK manifest 검사: package `com.gnssbench`, version `1.0-phase1`, minSdk 30, targetSdk 34, launchable Activity `com.gnssbench.ui.MainActivity` 확인.
- `adb devices`: 연결된 기기 없음. APK 설치/실행이나 실제 GNSS 측정을 수행했다고 주장하지 않습니다.

## 산출물

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- 크기: 20,651,619 bytes
- SHA256: `132235EEED7B1A42D0D221FB497D24D622DDD72C5E665613D3A038F50F2AE410`
- Unit test HTML: `app/build/reports/tests/testDebugUnitTest/index.html`
- Unit test XML: `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- Lint HTML: `app/build/reports/lint-results-debug.html`
- Lint text: `app/build/reports/lint-results-debug.txt`

같은 APK를 두 기기에 설치합니다. 다시 빌드하면 APK hash가 달라질 수 있으므로 새 산출물의 hash를 다시 기록하세요.

## 테스트 범위

거리 0/알려진 적도 1°/대칭/날짜변경선/대척점; invalid/NaN/Infinity GT; empty/single/multiple samples; R7 percentile endpoints와 CEP50/68/95/99; RMSE; inclusive within 1/2/3/5/10m; warm-up 제외; monotonic 측정 구간 시작 포함/끝 제외; stale/duplicate/future/mock 거부; stale satellite null; no-fix timeout; partial stop와 terminal error를 검증했습니다.

Export 테스트는 JSON의 64-bit nanosecond 정수/optional null 보존, 원시 관측 분리, CSV JSON payload/quote escaping, final summary 없는 Interrupted 기록, 손상된 journal line recovery warning, non-finite 센서 값의 null 처리를 검증했습니다.

## 아직 실기기 검증이 필요한 항목

TC58/TC501의 권한 UI, GNSS 위치/위성/raw event 실제 수신, 실제 chipset/band 관측, 5/15/30/60분 측정 및 warm-up, 화면회전·background·화면 꺼짐 유지, notification Stop, SAF 파일 저장 provider, 프로세스 종료 후 export 복구. 절차는 README에 있습니다.

이번 단계에서 측정된 실제 위치 정확도/CEP95, 기기간 성능 차이, correction 개선량, CPU/GPU/NPU 추론 성능은 없습니다. Classical/IMU/LiteRT는 REQUIREMENTS의 후속 phase이며 현재 APK에는 구현하지 않았습니다.
