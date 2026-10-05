# Phase 1 validation — 2026-10-05 (Asia/Seoul)

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
