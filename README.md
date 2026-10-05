# GNSS Benchmark — Phase 2 / Live analysis

## 실기기에서 바로 확인하는 오차

Static Benchmark에서 Start하면 실시간 대시보드로 전환합니다. **누적 CEP95를 cm와 m로 크게 표시**하고 평균·중앙값·RMSE·최대 오차, 최근 60초 CEP95, 표본 수, 1초 구간 수신 비율, 마지막 fix 경과 및 최대 무수신 간격을 갱신합니다. cm는 단위 변환일 뿐 cm급 정밀도나 95% 신뢰구간을 의미하지 않습니다. 이 실험의 관측 오차 백분위수이며 GT가 잘못되면 실제 정확도를 판단할 수 없습니다.

- 기본 프로토콜: 정지 상태 **warm-up 5분 + 측정 30분 = 총 35분**. 측정 구간 30분이 완료되면 해당 GT 기준 오차를 확인할 수 있습니다. 수신 불량으로 표본이 부족한 경우 그 사실도 함께 봐야 합니다.
- GT 좌표와 선택적인 측량 방법/출처, 불확실성(m)을 입력합니다. 모르면 비워두면 미확인으로 표시합니다. 입력 불확실성을 실제 오차에 임의로 더하거나 빼지 않습니다.
- 실시간 그래프: actual error / reported accuracy / rolling CEP95. 전체 시간 또는 최근 5분을 선택하고 그래프를 누르면 해당 표본 수치를 확인합니다. 3초 초과 수신 공백은 선을 끊습니다. reported accuracy는 Android의 68% 추정 반경으로 CEP95와 다른 지표입니다.
- GT 중심 산점도: 동/북 방향 동일 축척, GT 십자가, 누적 CEP95 원, 마지막 위치. 큰 오차도 축에 포함합니다. 표시 점이 많으면 시각화만 줄이고 통계·export는 전체 표본을 유지합니다.
- 최근 60초는 `(t-60,t]`의 시간 구간입니다. 처음에는 60초 미만의 표본만 존재하며, 무수신이 지속되면 rolling 지표는 —가 됩니다. 과거 값을 현재 값으로 채우지 않습니다.
- 화면 켜짐 유지 옵션을 제공합니다. 화면을 끄더라도 foreground service가 수집을 담당하지만 제조사별 정책은 실기기 검증이 필요합니다.

## 저장 세션 재열람 / 기기 비교

상단 NEXT로 **Sessions / Compare**를 엽니다. 내부 저장 목록에서 A로 열기/B로 비교를 선택하거나, 두 기기의 JSON export를 각각 가져옵니다. metadata와 측정 좌표를 읽어 같은 계산 코드로 통계를 재계산합니다. 원시 관측 배열은 streaming parser가 건너뛰므로 모든 raw event를 메모리에 올리지 않습니다. 시간 순서나 좌표가 잘못된 파일은 오류를 표시합니다. 불러오기는 최대 50,000개 위치 표본을 지원합니다. CSV import는 제공하지 않으며 CSV export는 유지됩니다.

A/B CEP95·RMSE·최대 오차·표본 수·측정시간·warm-up 및 각 그래프를 화면에서 비교합니다. protocol 불일치를 표시하며 환경/GT/배치가 같았다고 자동 판단하지 않습니다. 정상 종료 기록이 없으면 중단 상태로 표시합니다. 측정 중 파일 재열람은 비활성화됩니다. 내부에서 선택한 A 세션은 개별 CSV/JSON export가 가능하며 기존 최근 세션 export도 유지됩니다.

Zebra TC58과 TC501에서 **동일 APK**로 GPS_PROVIDER 위치를 수집하고, 독립적인 Ground Truth(GT)와 비교하는 offline-first 정지 benchmark 앱입니다. 지도/Google Maps API key/네트워크 권한은 사용하지 않습니다. Android GNSS 위치해를 그대로 baseline으로 쓰며 원시 관측으로 자체 위치해를 푸는 앱은 아닙니다. 보정·IMU·LiteRT는 후속 phase입니다.

전체 요구사항과 로드맵: [REQUIREMENTS.md](REQUIREMENTS.md). 구현 경계와 API 참고 자료: [ARCHITECTURE.md](ARCHITECTURE.md).

## Build

Android Studio에서 이 폴더를 엽니다. Android SDK Platform 34 / Build Tools 34.0.0, JDK 17 이상이 필요합니다. Gradle 8.7/AGP 8.5.2/Kotlin 및 Compose compiler 2.0.21을 고정했습니다. `local.properties`는 개인 SDK 경로이므로 Git에서 제외됩니다. 필요 시 `sdk.dir=...`를 설정합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Android 11/API 30 이상 지원. targetSdk 34는 직접 설치 실험용 설정이며 최신 Play Store 제출 요건을 의미하지 않습니다.

## 두 기기에 동일 APK 설치

기기에서 개발자 옵션/USB 디버깅을 켜고 PC 연결을 승인합니다. SDK platform-tools를 PATH에 추가하거나 adb의 절대 경로를 사용합니다.

```text
adb devices
adb -s TC58_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TC501_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TC58_SERIAL shell am start -n com.gnssbench/.ui.MainActivity
adb -s TC501_SERIAL shell am start -n com.gnssbench/.ui.MainActivity
```

SERIAL은 `adb devices`의 실제 식별자로 바꿉니다. 한 번 빌드한 동일 파일을 두 기기에 설치하고 파일 SHA256을 실험 노트에 기록하세요. 제조사 모델에 따른 기능 hard coding은 없습니다.

## 실행 방법

1. Device Info에서 제조사/모델/OS/API/공개 GNSS hardware 정보를 확인합니다. 정확한 위치 권한을 허용하고 시스템 위치를 켭니다. 대략적인 위치만 허용하면 Start는 실패 안내를 표시합니다. 권한을 영구 거부했다면 앱 권한 설정에서 변경합니다.
2. 측정 알림 권한은 선택 사항입니다. 장시간 측정은 location foreground service가 담당하고 알림에 Stop을 제공합니다. 알림 거부는 수집 자체를 막지 않습니다. 앱 내 Stop도 가능합니다.
3. 야외 open sky에서 기기를 고정합니다. 독립적으로 측량한 WGS84 latitude/longitude를 입력합니다. 현재 기기 fix의 좌표를 GT로 복사하면 실제 정확도 검증이 되지 않습니다. GT 자체의 오차도 결과에 영향을 줍니다.
4. Static Benchmark에서 기본 30분(선택 5/15/30/60분), warm-up 5분으로 Start합니다. debug APK의 명시적 개발용 옵션으로만 warm-up을 생략할 수 있습니다. 정식 실험에서는 끕니다.
5. WarmingUp → Measuring → Completed를 확인합니다. GNSS fix가 없어도 시간은 진행하며, 표본 0개는 통계 없음으로 표시합니다. Stop은 partial session으로 종료합니다. GPS가 꺼지거나 권한/저장 오류가 생기면 Error로 종료합니다.
6. Results에서 CEP95 및 모든 수평 오차 통계를 확인합니다. 측정 중에도 누적 통계를 볼 수 있습니다. Reset은 새 측정을 준비하고 저장 파일은 지우지 않습니다.
7. 두 기기를 가능한 같은 시간·장소·설치 자세로 독립 측정합니다. 날짜, 하늘 가림, 날씨, 주변 반사체, GT 측량 방식/불확실성, 기기 배치, battery/thermal 조건을 실험 노트에 기록합니다.

## 값의 의미

- **DETECTED**: Android API로 확인한 기기 정보/위성/관측 주파수. GNSS model이 미보고이면 추정 chipset 이름을 넣지 않습니다.
- **DERIVED**: 관측 기반 constellation 분포, 평균/최대 C/N0, 주파수 근사 band 분류. 원본 Hz가 기준이며 관측 대역은 전체 지원 대역과 다릅니다.
- **SPECIFICATION**: Phase 1은 제조사 자료를 자동 조회하지 않습니다.
- **Reported accuracy (m)**: Android의 추정 수평 불확실성. 실제 오차와 다릅니다.
- **Actual horizontal error (m)**: 유효한 GT와 각 위치의 mean Earth radius haversine 수평 거리. 고도는 제외됩니다. 구면 근사이므로 측량급 타원체 geodesic 정확도를 주장하지 않습니다.
- Mean/Median/RMSE/MAX: 측정 구간 오차의 평균/중앙값/제곱평균제곱근/최댓값. CEP50/68/95/99는 GT 중심 방사 오차의 백분위수입니다.
- CEP95: 해당 테스트 위치의 약 95%가 GT에서 이 거리 안에 있었다는 의미입니다. R7 선형 보간을 사용하므로 작은 표본에서 실제 포함 비율이 정확히 95%가 되는 것은 아닙니다. 해당 환경/세션의 결과이지 제조사 보장값이 아닙니다.
- Within Nm: 오차 `<= N`인 측정 표본의 비율(%). warm-up 제외, outlier filtering 없음. Null은 미관측이며 0으로 바꾸지 않습니다.
- Satellite 정보는 마지막 수신 시각을 표시합니다. 위치 sample에는 5초 이내에 수신한 snapshot만 연결하며 그 age와 timestamp를 export합니다. Android raw event는 별도 시간축을 보존합니다.

## 저장 / CSV·JSON export

세션은 앱 내부 `files/sessions/*.jsonl`에 순서대로 저장됩니다. location/위성/raw event를 독립적으로 기록하며 파일 쓰기는 별도 단일 I/O executor에서 처리합니다. 앱 삭제 시 내부 파일도 삭제되므로 필요한 결과는 export하세요. 데이터에는 정확한 위치가 포함됩니다.

측정 종료 후 JSON export 또는 CSV export → 시스템 파일 선택기에서 이름과 저장 위치를 정합니다. broad storage permission은 없습니다. 이전 프로세스가 종료되었거나 Reset했어도 **최근 저장된 세션**을 export할 수 있습니다. 내보내기 성공 메시지를 확인합니다. 이전 세션은 Sessions / Compare에서 A로 열고 개별 export할 수 있습니다.

JSON schemaVersion 1: `metadata`, `samples`, `satellites`, `rawObservations`, `events`, `recoveryWarnings`, `summary` 배열. 보통 metadata/summary는 각 1개입니다. metadata는 기기/앱 버전, GT, 시작 시각, warm-up/측정 설정, 거리/percentile 정의를 포함합니다. summary는 종료 시각, 상태/중단 이유, 실제 측정 경과와 통계입니다. sample의 phase로 WarmingUp/Measuring을 구분합니다.

CSV는 단일 헤더의 long-form CSV입니다. `record_type`으로 metadata/sample/satellites/raw/event/summary 등을 구분합니다. 위치/오차/위성 집계는 별도 열에 있으며 **모든 원본 필드와 nested raw metadata는 `payload_json`**에 보존됩니다. CSV reader로 읽은 뒤 해당 열을 JSON parse하세요. timestamp의 ms/ns 정수는 Excel 부동소수점으로 변환하면 정밀도가 손실될 수 있으므로 정수/문자열로 유지합니다.

프로세스 강제 종료/기기 재부팅 후 측정을 자동 재개하지 않습니다. final summary가 없으면 export에 Interrupted를 명시합니다. 마지막 줄이 손상되면 recovery warning으로 보존합니다. 이 경우 정상 완료 통계를 만들어 내지 않습니다. 각 journal append는 파일 stream을 닫아 flush하지만 전원 손실에 대한 fsync 보장은 없습니다.

## 장시간 측정과 검증 범위

화면회전/Activity 재생성은 수집 데이터를 소유하지 않으므로 실험을 초기화하지 않습니다. foreground service와 active-session partial wakelock을 사용해 화면 꺼짐/background 수집을 지원합니다. OS 강제 종료/제조사 전력 정책/권한 취소까지 측정 연속성을 보장할 수는 없으며, 중단은 정상 연속 세션으로 위장하지 않습니다. 앱에 INTERNET 권한은 없으나 Android GNSS 플랫폼 자체의 assistance 사용 여부를 통제하지 않습니다.

실기기 검증 절차:

- precise/approximate/거부/설정 복귀, GPS off 시작 금지, 측정 중 GPS off Error 확인.
- debug warm-up skip으로 5분 세션 실행, 5분 warm-up + 30분 및 60분 정식 세션 실행.
- 회전/Home/화면 off 후 샘플 시간 간격·수량과 notification Stop 확인.
- raw event 미수신 상태와 위치 fix 수신 상태가 독립적으로 표시되는지 확인.
- 두 export 형식의 동일 세션 metadata/측정 표본 수/CEP95 일치 확인.
- 강제 종료 후 재실행하여 최근 세션 Interrupted export 확인.
- GT와 실제 관측된 좌표/오차를 독립 계산기로 교차 검증.

자동 검증 결과 및 실기기 연결 여부는 [VALIDATION.md](VALIDATION.md)에 기록합니다. 실기기 측정을 수행하지 않은 상태에서 정확도 개선 수치나 하드웨어 지원 기능을 주장하지 않습니다.
