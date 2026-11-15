# 도착케어 검증 기록

검증일: 2026-11-15 (KST). 실제 로그·APK·기기 화면을 기준으로 기록합니다.

## 빌드와 서명

- Kotlin 1.9.24 / JDK 17 / Gradle 8.7 / Android Gradle Plugin 8.5.2.
- compileSdk / targetSdk 34, minSdk 28.
- `assembleDemoRelease`, `assemblePleosRelease`, `testDemoDebugUnitTest`, `lintDemoDebug`: **BUILD SUCCESSFUL**.
- 상태 머신 단위 테스트 **22개, 실패 0, 오류 0**.
- 시연 전용 키로 서명한 Release APK. APK Signature Scheme v2 검증 성공.
- Android Studio 프로젝트 구조를 제공하며 실제 빌드는 Gradle CLI로 수행했습니다. 이 PC에는 Android Studio IDE가 설치돼 있지 않아 IDE GUI 실행을 검증했다고 주장하지 않습니다.

## 실행 환경과 확인 범위

| 환경 | 실제 관찰 |
|---|---|
| Pleos Connect 2.1.0 / API 34 / Android Emulator 37.2.13 | 공식 이미지 다운로드·체크섬 확인·AVD 생성·ADB 연결. 초기 부팅 중 차량 서비스와 SystemServer 재시작. 안정 실행·SDK 연결 성공 미확인 |
| Google APIs Android 14 / API 34 / Emulator 37.2.13 | Debug APK 설치 및 MainActivity `Status: ok`, 첫 화면 확인. 이어서 System UI ANR 및 시스템 재시작이 관찰되어 전체 시연 환경으로 사용하지 않음 |
| AOSP Android 9 / API 28 / Emulator 35.6.11 / 1280×720 | 서명 Release APK 설치·Activity 실행 성공. 병원 전체 여정, 유일한 보호자, 도어 자동 완료 방지 흐름을 실제 터치로 확인. 최종 UI 보완본 검증 결과는 아래 기록에 갱신 |

## SDK 범위

- Vehicle **2.3.2**, NaviHelper **3.0.5**의 공개 Maven 배포물을 내려받아 실제 함수 서명을 확인하고 컴파일했습니다.
- `pleos` 빌드는 실제 SDK 초기화와 연결 진단, Vehicle 도어 capability·위치 조회, AAOS UX 제한 관찰 코드를 포함합니다.
- NaviHelper는 초기화와 `getDestinationInfo()` 읽기 전용 진단을 시도합니다. 가상 시설에는 실제 좌표를 부여하지 않았으므로 `requestRoute()`를 호출하지 않습니다.
- 두 빌드의 경로와 도착은 시뮬레이션입니다. 실제 내비 경로 탐색, 실내 측위, 실제 차량 테스트, 제조사 인증·승인을 완료한 것으로 표현하지 않습니다.

## 발견한 문제와 조치

1. 첫 에뮬레이터 실행에서 기본 `.android` 폴더가 없어 잠금 파일 생성 오류가 반복됐습니다. 폴더를 생성하고 해당 개발 프로세스만 재시작했습니다.
2. Pleos 부팅 로그에서 `eth0 is not ready`, DDS participant 생성 실패, `CarServiceHelper did not connect`, SystemServer Watchdog 재시작을 확인했습니다. 네트워크 호환 옵션을 시험했으며, 에뮬레이터 서비스 오류와 앱 오류를 구분했습니다.
3. 빌드와 에뮬레이터를 동시에 실행했을 때 Windows 여유 메모리가 약 270MB로 줄었습니다. 개발용 Java 프로세스를 정리하고 Gradle 힙 1GB·단일 worker로 조정해 빌드와 실행을 순서대로 진행했습니다.
4. Android 14 시스템 UI ANR을 관찰해, 최종 핵심 시연은 더 가벼운 API 28 AOSP와 공식 안정판 Emulator 35.6.11을 사용하도록 변경했습니다.
5. UI 자동화에서 새 화면의 접근성 루트를 읽지 못하는 경우를 발견했습니다. 오래된 XML을 성공 결과로 사용하지 않도록 수정했고, 앱의 중복 화면 갱신도 한 번으로 합쳤습니다.
6. 함께 이동 모드의 주차 안내 문구를 바로잡고, 시뮬레이터의 ‘다음 실패’와 ‘다음 응답 지연’을 서로 배타적으로 선택하도록 보완했습니다.

Lint 오류는 없습니다. 고정 가로 방향과 Android 12+ 데이터 추출 규칙에 대한 경고 2개가 남아 있습니다. 현재 앱은 로그인·서버·영구 저장 기능을 사용하지 않으며 백업을 비활성화했습니다. 구형 전체화면 API와 Vehicle capability overload의 deprecation 경고는 후속 개선 대상입니다.

호스트는 Intel i5-1235U, RAM 16GB, Intel Iris Xe입니다. Pleos 공식 문서의 외장 GPU 최소 사양에는 미달합니다. 이것만으로 모든 부팅 오류의 원인이라고 단정하지 않습니다.
