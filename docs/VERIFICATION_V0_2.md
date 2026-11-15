# 도착케어 v0.2.0 검증 기록

검증일: 2026-11-15 KST. 로그와 실제 설치 APK를 기준으로 작성합니다. v0.1.0의 [기존 기록](VERIFICATION.md)은 그대로 보존합니다.

## 빌드·서명

- 애플리케이션 코드 커밋: `6c828bb3114757ba3da75fc487f8cad31e1a5702`.
- [GitHub Actions 43687362639](https://github.com/junyong290/arrival-care-mvp/actions/runs/43687362639): demo 단위 테스트·Lint·두 Release 빌드, Pleos 두 Release 빌드 모두 성공.
- 로컬 Gradle의 `testDemoOfflineDebugUnitTest`: 기존 Journey 22개 + ResponsivePolicy 10개 = **32개, 실패 0, 오류 0**. 동일 테스트를 위 CI에서 재실행했습니다.
- 기존 `Journey.kt`와 `JourneyTest.kt`는 v0.1.0 대비 내용 변경이 없습니다.
- JDK 17 / Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24 / compile·target SDK 34 / min SDK 28.
- CI unsigned Release APK 네 개를 내려받아 ZIP 정렬 확인 후 로컬 시연 인증서로 서명했습니다. **APK Signature Scheme v3** 검증 성공. API 28 설치 성공.
- 공개 인증서 SHA-256: `8f5e15e33f376db205af85636df6d596c64a262d5ad3261ea80e56a5f817f289`. v0.1.0과 동일합니다. 개인 키와 비밀번호는 공개하지 않습니다.
- 앱 버전 `0.2.0`, versionCode `2`. Android Studio 프로젝트를 제공하며 실제 빌드는 CLI·CI로 검증했습니다. Android Studio IDE GUI 실행 검증은 아닙니다.

[단위 테스트 결과](../evidence/v0.2.0/unit-results.json) · [CI 결과](../evidence/v0.2.0/build-results.json) · [APK 해시·서명·ABI](../evidence/v0.2.0/apk-results.json)

## 실행 환경

테스트 호스트는 Windows / RAM 16GB / Intel Iris Xe, 시연 에뮬레이터는 Android Emulator 35.6.11 / AOSP Android 9(API 28) / x86_64입니다. `arrivalcare-demo-offline-0.2.0.apk` 설치 후 MainActivity `Status: ok`를 확인했습니다.

실제 Android 스마트폰은 연결되지 않았으며 사용자가 **기기 검증은 추후 진행**하기로 결정했습니다. 에뮬레이터의 스마트폰 크기 화면을 실제 스마트폰 테스트로 보고하지 않습니다.

## 실제 UI 시나리오

ADB로 새 UI 계층의 실제 버튼을 눌렀습니다. 앱 상태를 직접 주입하거나 화면을 합성하지 않았습니다.

| 시나리오 | 결과 |
|---|---|
| 부모님·병원 → 하차 → 주차 → 재합류 → 완료 | PASS |
| 유일한 보호자일 때 하차 생략 | PASS |
| 도어 열림 진행 차단, 닫힘만으로 자동 완료 금지 | PASS |
| 차량 연결 실패 시 직접 문 확인 추가 | PASS |
| 주행 중 입력 잠금·정차 복귀 | PASS |
| 경로 요청 실패 후 재시도 | PASS |
| 이전 요청의 늦은 응답 무시 | PASS |
| 쇼핑몰·휠체어·함께 이동 정보 변경 | PASS |
| D/P/M 상세와 지도 상태·키 해시 표시 | PASS |
| 창 크기 변경 후 미확인 하차 단계·비활성 확인 버튼 유지 | PASS |

핵심 여정과 유일한 보호자 흐름은 **720×1280 / density 320 / 360×640dp** 세로 화면에서 통과했습니다. 앞의 9개 시나리오는 스크롤 변수를 줄인 **720×1280 / density 160** 화면에서도 모두 통과했습니다.

가로 회전 설정만으로는 실제 패널이 회전하지 않는 API 28 headless 환경 문제가 있었습니다. 이를 회전 통과로 처리하지 않고 `NOT_VERIFIED`로 정정했습니다. 대신 `wm size/density`로 실제 앱 창을 **1280×720 / density 160 → 720×1280 / density 320**으로 변경하여 지도·안내의 좌우/상하 배치를 UI 계층 좌표로 검사하고, 하차 미확인 상태가 유지됨을 재검증했습니다. 물리 패널 안의 레터박스는 창 크기와 구분합니다. 이 검사는 Pleos 시스템 멀티윈도우나 실제 스마트폰 센서 회전 검증은 아닙니다.

[작은 세로 화면 결과](../evidence/v0.2.0/portrait/ui-results.json) · [9개 시나리오 결과](../evidence/v0.2.0/large-portrait/ui-results.json) · [창 변경 결과](../evidence/v0.2.0/resize/ui-results.json) · [가로 배치 좌표](../evidence/v0.2.0/resize/landscape-layout.json) · [세로 배치 좌표](../evidence/v0.2.0/resize/portrait-layout.json)

이후 AVD 물리 해상도를 **1280×720 / density 160**으로 설정해 재부팅하고, 최종 서명 APK의 전체 여정을 다시 통과했습니다. 이 환경의 가로 스크린샷은 레터박스나 합성이 아닌 원본 캡처입니다. [가로 여정 결과](../evidence/v0.2.0/landscape/ui-results.json) · [실제 가로 배치](../evidence/v0.2.0/landscape/layout.json)

## 실행 영상

`arrivalcare-demo-0.2.0.mp4`는 Android `screenrecord`의 실제 녹화입니다. **63.65초 / 1280×720 / H.264 / 음성 없음**. 검증 시 읽은 버튼 좌표를 같은 APK·같은 에뮬레이터 크기에서 재생했고, 앱 상태를 직접 바꾸지 않았습니다. 부모님·병원 계획, 하차 두 가지 직접 확인, 주차 완료, 재합류와 완료를 포함합니다.

녹화 후 새 UI 계층에서 `도착을 함께 마쳤어요`를 검사했고, MP4의 마지막 프레임에서도 완료 화면을 확인했습니다. 인코더가 변화가 있는 화면을 기록하므로 가변 프레임률입니다. 배포 파일은 재생 시작용 메타데이터만 앞쪽으로 이동했으며 화면이나 재생 시간을 합성·가속하지 않았습니다.

[영상 다운로드](https://github.com/junyong290/arrival-care-mvp/releases/download/v0.2.0/arrivalcare-demo-0.2.0.mp4) · [영상 마지막 프레임](../evidence/v0.2.0/video-final-frame.png)

## Kakao·Pleos 구분

| 항목 | 확인 결과 |
|---|---|
| Kakao 2.15.2 SDK | 실제 Maven 의존성 다운로드·API 서명 확인·두 ARM 변형 컴파일 성공 |
| 네이티브 ABI | 배포 AAR/APK에 `arm64-v8a`, `armeabi-v7a`만 포함. x86 네이티브 지원 없음 |
| x86_64 설치 확인 | Kakao ARM APK 설치가 `INSTALL_FAILED_NO_MATCHING_ABIS`로 거부됨을 실제 확인. 같은 기기에서 offline APK는 정상 실행 |
| 실제 카카오 지도 타일 | 사용자 키 미발급. 인증·네트워크 렌더링 성공 미검증 |
| SDK 없는 대체 지도 | offline APK에서 실제 화면·D/P/M 상세·여정 동작 검증 대상 |
| 키 누락/연결 실패 fallback 코드 | 실행 조건 단위 테스트 통과. ARM에서 SDK 실패 콜백을 발생시키는 실기기 검증은 후속 필요 |
| Vehicle 2.3.2 / NaviHelper 3.0.5 | 실제 의존성·어댑터 컴파일 성공. 서비스 연결 성공과 구분 |
| 실제 경로 요청 | 모든 변형 Mock. `NaviHelper.requestRoute()`를 호출하지 않음 |

Kakao는 실제 지도를 표시하는 SDK이며 내비게이션 경로 탐색과 다릅니다. D/P/M 좌표는 샘플 오버레이로, 가상 병원·쇼핑몰의 실제 위치나 검증된 승하차 장소를 뜻하지 않습니다. 샘플 좌표를 실제 내비게이션 목적지로 전송하지 않습니다.

설정: [카카오 키·패키지·인증서 해시 안내](KAKAO_SETUP.md). 실제 지도 검증에는 등록된 네이티브 키와 ARM 기기가 필요합니다.

[ARM APK 설치 제한 결과](../evidence/v0.2.0/abi-install.json)

## 발견한 문제와 조치

- 로컬 Gradle과 에뮬레이터를 동시에 실행할 때 메모리 여유가 부족해졌습니다. 최종 네 변형 빌드는 CI에서 수행하고 로컬에서는 서명과 설치 검증을 진행했습니다.
- UIAutomator가 간헐적으로 `null root node`를 반환했습니다. 새 XML 생성 확인·재시도·압축 계층 조회를 사용하며 오래된 XML을 통과 근거로 사용하지 않습니다.
- 작은 세로 화면에서 스크롤 위쪽 제목을 다시 찾는 자동화가 중단됐습니다. 실제 화면과 계층을 확인하고 스와이프 간격·방향 전환을 보완했습니다. 중단된 실행은 통과 결과에 포함하지 않습니다.
- API 28의 `user_rotation` 요청이 실제 화면 방향을 바꾸지 않아 검사 기준을 강화했습니다. 요청한 방향만으로 통과 처리하지 않고 실제 창 좌표를 확인합니다. `wm size`는 물리 패널을 회전시키지 않으므로 캡처 파일 크기만으로 창 방향을 판단하지 않습니다.
- 카카오 ARM APK를 x86용으로 포장하지 않았습니다. 네이티브 SDK를 제외한 `offline` 변형을 별도 제공해 지원하지 않는 환경에서도 핵심 여정이 실행되도록 했습니다.
- 앱 키가 없는 상태를 지도 연결 성공으로 표시하지 않습니다. 지도 상태 화면에서 실제 상태·패키지·인증서 키 해시를 확인할 수 있습니다.

Lint 오류 **0개**, 경고 **12개**: ARM 전용 SDK의 ChromeOS ABI(1), 데이터 추출 규칙(1), 그리기 중 작은 객체 생성(5), 코드로 생성하는 View 생성자(3), 터치 리스너 접근성 정적 분석(2). 버튼 리스너는 이벤트를 소비하지 않아 기본 클릭 동작을 유지하고, 지도 입력 차단 View는 의도적으로 터치를 소비합니다. 고정 가로 방향 경고는 방향 제한 제거로 해결했습니다.

## 후속 검증

- 실제 Android 스마트폰에서 세로·가로·큰 글꼴·백그라운드 복귀·저장 상태 복원 확인.
- 등록된 Kakao 키로 실제 타일, D/P/M label 탭, 인증 실패·네트워크 실패·재시도 확인.
- 정상 Pleos 환경에서 시스템 멀티윈도우와 Driving view 공존, 실제 Vehicle·Car UX·NaviHelper 서비스 응답 확인.
- 실제 경로 안내를 추가하려면 검증된 시설 데이터·서비스 권한·현장 검증이 먼저 필요합니다.

## 기존 배포 보존

v0.1.0 태그 커밋, Release ID, 제목·본문, 기존 7개 자산의 ID·이름·크기·SHA-256 digest가 작업 전 기준과 일치합니다. [비교 결과](../evidence/v0.2.0/v0.1-preserved.json). v0.2.0은 별도 태그와 Release로 배포합니다.
