# 도착케어 v0.3.0 검증 보고서

검증일: 2026-11-15. 앱 소스 기준: `4f5ff2a83b62bd84ec6a2c1b83a67396eb5ae96f`. 이후 문서·실행 증거·검증 도구 커밋은 APK 코드 변경을 포함하지 않습니다.

## 결과와 한계

네 가지 Release 변형을 로컬에서 빌드하고 기존 인증서로 서명했습니다. 가상 시설의 추천·하차·주차·재합류는 실행 가능한 데모이며, 실제 시설 경로 안내로 제공하지 않습니다.

- 실제 ARM 스마트폰 검증은 사용자의 요청으로 오늘 진행하지 않았습니다. **카카오 실제 타일 표시·인증 성공은 미검증**입니다. 네이티브 키가 포함된 ARM APK를 준비한 사실과 구별합니다.
- Android Studio용 Gradle 프로젝트이며, CLI와 CI에서 검증했습니다. Android Studio IDE의 GUI 실행 검증을 했다고 주장하지 않습니다.
- 실제 서울아산병원의 대표 위치·공식 정보만 사용합니다. 검증되지 않은 D/P/M·무단차 동선을 생성하지 않으며 계획을 보류합니다.
- 실연동 내비게이션의 도착 이벤트에 따른 자동 단계 전환은 아직 활성화하지 않았습니다. 실제 목적지 요청 성공과 차량 도어 신호 수신은 아래 환경별 결과와 구별해야 합니다.

## 빌드·단위 테스트·서명

JDK 17 / Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24 / compileSdk 34 / minSdk 28.

```powershell
.\gradlew.bat :app:testDemoOfflineDebugUnitTest :app:assembleDemoOfflineRelease :app:assembleDemoKakaoRelease :app:assemblePleosOfflineRelease :app:assemblePleosKakaoRelease --no-daemon --console=plain
```

로컬 결과: **BUILD SUCCESSFUL**, 11분 51초. 213 tasks 중 45 실행, 168 up-to-date. [동일 앱 커밋의 CI](https://github.com/junyong290/arrival-care-mvp/actions/runs/43704419836)에서도 네 변형 빌드·테스트·demo lint가 통과했습니다. CI APK는 로컬 네이티브 키와 개인 서명을 포함하지 않습니다.

| 테스트 | 개수 | 결과 |
|---|---:|---|
| 기존 JourneyTest | 22 | 통과 |
| 기존 ResponsivePolicyTest | 10 | 통과 |
| 신규 ArrivalRecommendationTest | 16 | 통과 |
| 합계 | **48** | 실패·오류·건너뜀 0 |

기존 32개 테스트 파일은 v0.2.0과 동일하게 보존했습니다. 신규 검사는 조건별 D/P/M 변경, 유일 보호자, 접근성 누락, 실제 시설 보류, 좌표·POI·권한 검증, 경로 승인·중복 요청·오래된 응답 처리를 확인합니다. 원본 결과는 [unit](../evidence/v0.3.0/unit/summary.json)에 있습니다. 공개 XML에서는 호스트 이름만 제거했습니다.

모든 APK에 `apksigner verify`, `zipalign -c 4`, 패키지·버전·서명 인증서 검사 완료. 상세 크기·SHA-256은 [apks.json](../evidence/v0.3.0/apks.json), 배포 파일 전체 체크섬은 Release의 `SHA256SUMS.txt`를 사용하세요.

| APK | 패키지 | 네이티브 키 | 지원 ABI |
|---|---|---|---|
| demo-offline | `ai.arrivalcare.app.demo` | 제외 | 순수 JVM 코드, ARM/x86 |
| demo-kakao-arm | `ai.arrivalcare.app.demo` | 로컬 설정 포함 | arm64-v8a, armeabi-v7a |
| pleos-offline | `ai.arrivalcare.app.pleos` | 제외 | Pleos 플랫폼 필요 |
| pleos-kakao-arm | `ai.arrivalcare.app.pleos` | 로컬 설정 포함 | ARM Pleos 플랫폼 필요 |

버전은 모두 **0.3.0 / versionCode 3**. 현재 Release 인증서의 카카오 키 해시: `VRrndCnhcmPis6Eo0kxawHoNJzU=`. [등록 안내](KAKAO_SETUP.md)에서 패키지·키 해시·카카오맵 사용 설정을 확인합니다. 네이티브 앱 키는 클라이언트 APK에 포함되는 값이므로 콘솔의 패키지·서명 제한이 필요하며, 저장소·로그에는 키 값을 공개하지 않습니다.

## 실제 UI 실행

최종 서명 `demoOffline` APK를 설치하고, 기기 내부 `base.apk`의 SHA-256이 배포 APK와 일치함을 확인했습니다. 앱 내부 상태를 주입하지 않고 ADB의 실제 터치·스크롤과 새 UI hierarchy를 사용했습니다.

| 환경 | 실제 검사 | 결과 |
|---|---|---|
| Android API 28 x86_64 에뮬레이터, 1280×720 / 160dpi | 전체 UI 시나리오 15개 | **15/15 통과** |
| 같은 AVD의 실제 window size/density 변경 | 가로↔세로 레이아웃, 미확인 하차 상태 유지 | 통과; 물리적 회전 센서 검사는 아님 |
| GitHub Actions Ubuntu KVM, API 28 x86_64, 720×1280 / 320dpi | 전체 여정·도어 연결 실패·다크 테마·마커/줌 | **4/4 통과**, CI debug 서명 |

15개 항목: 부모님 병원 방문 전체 여정, 유일 보호자의 하차 생략, 도어 개폐만으로 자동 완료 금지, 연결 실패 직접 확인, 주행 제한, 경로 재시도, 늦은 응답 무시, 쇼핑몰·휠체어 추천, D/P/M 상세와 줌·복귀, 창 크기 변경, 접근성 누락 보류, 실제 시설에 가상 D/P/M 생성 금지, 테마 전환, 백그라운드 복귀 시 재확인, 경로 명시적 승인.

기록: [UI 결과](../evidence/v0.3.0/landscape/ui-results.json), [설치 해시·환경](../evidence/v0.3.0/landscape/runtime-scope.json). 가로 계획·하차·재합류와 다크 선택 항목의 실제 캡처를 육안 확인했습니다.

세로 실행은 [CI 43711064419](https://github.com/junyong290/arrival-care-mvp/actions/runs/43711064419)에서 완료했습니다. 저장소의 앱 디렉터리가 최종 앱 커밋 `4f5ff2a`와 동일한지 `git diff --exit-code`로 검사한 뒤, **별도 CI debug 인증서로 빌드한 APK**를 설치했습니다. 배포 Release APK와 동일한 바이너리·인증서라고 주장하지 않습니다. 설치 파일 해시는 [세로 환경 기록](../evidence/v0.3.0/portrait/runtime-scope.json)에 있습니다. 세로 계획·하차·추가 도어 확인·야간 선택 상태의 캡처를 육안 확인했습니다.

초기 CI는 비공개 초안 Release가 읽기 전용 토큰에 보이지 않아 다운로드 단계에서 종료됐습니다. 최종 CI는 토큰을 읽기 전용으로 유지하고 같은 소스의 debug 빌드로 검사했습니다. 실제 ARM 스마트폰 검증과 Release APK의 별도 세로 AVD 전체 시나리오 검증은 완료하지 못했습니다.

### 실제 앱 영상

`arrivalcare-demo-0.3.0.mp4`: **67.36초, 1280×720**, 부모님·별도 보호자의 병원 방문 전체 여정. Android `screenrecord` 원본을 MP4 faststart로 재포장했으며, 컷 편집·배속·화면 합성을 하지 않았습니다. 디코딩 검사와 5초·24초·48초·마지막 구간의 실제 영상 프레임을 육안 확인했고, 녹화 종료 UI에서도 `도착을 함께 마쳤어요`를 확인했습니다. [영상 메타데이터](../evidence/v0.3.0/movie/video.json).

휠체어·접근성 보류와 경로 실패·도어 누락 복구는 [3개 시나리오 실제 터치 결과](../evidence/v0.3.0/movie/ui-results.json)와 `21-movie-accessibility.png`, `22-movie-retry-recovered.png`, `23-movie-door-recovered.png`로 제공합니다. 세 시나리오를 모두 하나의 영상에 넣었다고 주장하지 않습니다.

첫 연속 녹화는 PC/ADB 지연으로 제한 시간을 초과해 제외했습니다. 핵심 여정에 집중한 최종 녹화는 별도로 완료했습니다. 녹화 도구의 완료 문구 검사 오타도 수정했으며, 실제 생성된 영상과 저장된 UI XML을 정확한 완료 문구로 다시 검증했습니다.

## Pleos 2.1.0 진단

공식 Pleos Connect **2.1.0 / Android API 34 / x86_64** 이미지를 Android Emulator **35.6.11**, 4GB RAM, 2560×1440, cold boot로 시도했습니다. 일반 AVD와 Gradle은 종료한 상태였습니다.

첫 시도는 **300초 안에 부팅 완료를 확인하지 못했습니다.** 추가 진단에서 뒤늦게 `sys.boot_completed=1`과 Virtual Console HTTP 200을 확인해 설치·실행 검사를 이어갔습니다. 이전 버전 설치 기록을 v0.3.0 검증으로 사용하지 않습니다. [첫 진단 결과](../evidence/v0.3.0/pleos/result.json).

최종 `pleosOffline` 설치 명령은 **70초 시간 초과**가 났고, 설치 직후와 추가 대기 후의 `base.apk` SHA-256은 모두 최종 Release APK와 달랐습니다. 따라서 **v0.3.0 Pleos 설치·실행, Vehicle 도어 신호·차량 위치·NaviHelper 경로 성공은 미검증**입니다. 부팅 신호 없이도 Package Manager 응답을 확인하는 제한된 진단을 수행했지만, 이전 설치 파일을 새 APK의 실행 성공으로 사용하지 않았습니다. [설치 관찰 기록](../evidence/v0.3.0/pleos/install-observations.json), [최종 해시 비교](../evidence/v0.3.0/pleos/final-result.json).

재시도 중 Python에서 ADB를 실행할 때 Windows `WinError 5`가 한 차례 발생했습니다. 직접 ADB 연결이 정상인 것을 확인한 뒤 명시적 SDK 경로로 재시도했습니다. Virtual Console HTTP 200은 확인했으나 브라우저 제어가 두 번 시간 초과되어 UI 조작·차량 신호 변경은 검증하지 못했습니다. HTTP 응답만으로 Vehicle 서비스가 연결됐다고 판단하지 않습니다.

호스트는 Windows 11 Home 10.0.26200, 사용 가능 총 메모리 약 15.7GiB, Intel Iris Xe Graphics입니다. 공식 [에뮬레이터 GPU 요구사항](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)의 외장 GPU 기준을 충족하지 않습니다. 자원 부족이 영향을 줄 수 있으나 부팅 실패의 유일한 원인이라고 확정하지 않습니다. 검증 후 이 작업에서 시작한 에뮬레이터를 종료했습니다.

로컬 API 28 세로 AVD도 첫 부팅이 제한 시간을 넘었고, RAM을 768MB로 낮춘 재시도에서는 설치 해시 일치까지 확인했으나 Android의 **“System UI isn't responding”** 창 때문에 UI 검사를 진행하지 못했습니다. 이를 도착케어의 시나리오 통과나 앱 크래시로 잘못 분류하지 않았습니다. 별도 CI 세로 검증은 아래 결과로 구분합니다.

## 구현과 시뮬레이션의 경계

| 항목 | 구현 | 검증 범위 |
|---|---|---|
| 추천·단계 전환·안전 확인·승인 | Kotlin 상태 머신과 규칙 엔진 | 단위 테스트와 실제 터치 UI |
| 가로/세로·라이트/다크·지도 마커·줌 | 실제 Android View | 에뮬레이터 실행 캡처 |
| D/P/M·시연 차량 위치·도착 | 가상 시설 fixture와 Mock 이벤트 | 실제 시설의 위치·접근성 보증 아님 |
| demo 도어·주행·경로 성공/실패/지연 | 독립 Mock Gateway | 테스트 시뮬레이터 |
| Kakao Maps 2.15.2 | 초기화, MapView, 마커, 콜백, 오류·시간 초과 대체, 수명주기 | 키 포함 빌드와 ABI 검사. 실제 ARM 타일 미검증 |
| Vehicle 2.3.2 | 기능 지원 확인, 4개 도어 조회·이벤트, 구독 해제, 신호 시간 초과 | 컴파일 확인. 실제 수신은 환경별 진단 참조 |
| 차량 UX 제한 | Car UX Restrictions, 연결 불명확 시 입력 잠금 | demo 시연과 Pleos 진단 구분 |
| 실제 차량 위치 | NaviHelper 조회·신선도 제한·foreground polling | 전화기 GPS가 아님. 실제 서비스 필요 |
| NaviHelper 3.0.5 | requestRoute, 권한·연결·검증 좌표/POI·사용자 동의, 응답 목적지 일치 확인 | 현재 실제 D/P 미검증으로 경로 요청 차단. 실제 요청 성공 미검증 |

실연동 실패를 Mock 성공으로 바꾸지 않습니다. SDK의 전역 이벤트에는 요청 ID가 없어 구독 세대·로컬 요청 토큰·목적지 일치 검사를 조합했습니다. 실제 서비스의 이벤트 순서와 외부 앱의 동시 경로 변경은 차량에서 추가 검증해야 합니다.

## 발견한 오류와 처리

- NaviHelper `RouteInfo` 컴파일에서 필수 `poiSubId` 누락을 발견해 공식 설치 SDK 시그니처에 맞게 수정했습니다. 이후 네 변형 빌드가 통과했습니다.
- 작은 세로 창에서 하차 확인 항목이 잘리는 현상을 발견해 확인 영역을 280dp, 추가 도어 확인 시 340dp 우선 확보했습니다. 계획 화면의 동행 조건 행은 간결하게 접었습니다.
- 작은 지도에서 보조 버튼·캡션이 마커와 겹치는 문제를 수정했습니다. 높이가 부족하면 보조 정보부터 접고 D/P/M 확인을 유지합니다.
- 세로 화면의 일부 아이콘 표시 문제를 명시적 glyph bounds로 수정했습니다. 야간 선택 항목의 글자 대비를 높였습니다.
- 계획 보류 상태에서 D/P/M 마커가 이전 후보를 설명하는 문제를 수정해 현재 보류 이유를 보여줍니다.
- API 28 에뮬레이터의 `user_rotation` 설정은 실제 화면 방향을 바꾸지 않았습니다. 방향 테스트는 실제 window size/density 변경과 가로·세로 AVD 부팅으로 수행하며 회전 센서 테스트라고 표시하지 않습니다.
- UiAutomator에서 간헐적인 `null root node`가 발생해 새 UI dump 성공 여부를 검사하고 제한된 횟수로 재시도했습니다. 이전 XML을 성공 근거로 재사용하지 않습니다.
- Vehicle SDK의 기존 capability API에는 deprecated 경고가 있습니다. 현재 설치 SDK에서 제공되어 컴파일되지만 후속 SDK 업그레이드 시 새 API로 이관할 항목입니다.

## 보존과 후속 검증

v0.1.0, v0.2.0의 기존 태그·Release 설명·asset ID·크기·digest를 배포 전 확인했으며 새 배포 후에도 같은 항목을 비교합니다. 기존 기획 PDF와 접수 자료는 수정하지 않았습니다. 이 배포가 공모전 접수 자체를 의미하지는 않습니다.

남은 외부 환경 검증: ARM 스마트폰의 실제 카카오 타일·마커·네트워크 차단 복구, 적합한 Pleos 장비의 도어 이벤트·UX 제한·차량 위치·승인된 실제 경로 요청. 사용자 제공 키를 저장소에 커밋하지 않고, 실제 기기 준비 후 동일 인증서 APK로 확인할 수 있습니다.

공식 근거: [Kakao 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/), [MapView 수명주기](https://apis.map.kakao.com/android_v2/docs/getting-started/mapdraw/), [Pleos 레이아웃](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout), [NaviHelper 목적지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations), [시설 데이터 출처](UI_V0_3.md).
