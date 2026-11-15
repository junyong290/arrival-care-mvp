# 도착케어 검증 기록

검증일: 2026-11-15 (KST). 실제 로그·APK·기기 화면을 기준으로 기록합니다.

## 빌드와 서명

- Kotlin 1.9.24 / JDK 17 / Gradle 8.7 / Android Gradle Plugin 8.5.2.
- compileSdk / targetSdk 34, minSdk 28.
- `assembleDemoRelease`, `assemblePleosRelease`, `testDemoDebugUnitTest`, `lintDemoDebug`: **BUILD SUCCESSFUL**.
- 상태 머신 단위 테스트 **22개, 실패 0, 오류 0**.
- 시연 전용 키로 서명한 Release APK. APK Signature Scheme v2 검증 성공.
- Android Studio 프로젝트 구조를 제공하며 실제 빌드는 Gradle CLI로 수행했습니다. 이 PC에는 Android Studio IDE가 설치돼 있지 않아 IDE GUI 실행을 검증했다고 주장하지 않습니다.

최종 배포 APK SHA-256:

```text
367402c54ac0bd4d0d0078ed14c4778af2552f6a3dd9f781cf80e6f718dc63c2  arrivalcare-demo-0.1.0.apk
614f3164051b3ac1b53bc2486295928e828846cacf5a53d57cc95703e9c89704  arrivalcare-pleos-probe-0.1.0.apk
```

두 APK의 시연 서명 인증서 SHA-256은 `8f5e15e33f376db205af85636df6d596c64a262d5ad3261ea80e56a5f817f289`입니다. 인증서 개인 키와 비밀번호는 배포 파일과 저장소에 포함하지 않았습니다.

## 실행 환경과 확인 범위

| 환경 | 실제 관찰 |
|---|---|
| Pleos Connect 2.1.0 / API 34 / Android Emulator 37.2.13 | 공식 이미지 다운로드·체크섬 확인·AVD 생성·ADB 연결. 초기 부팅 중 차량 서비스와 SystemServer 재시작. 안정 실행·SDK 연결 성공 미확인 |
| Pleos Connect 2.1.0 / API 34 / Emulator 35.6.11 | 부팅 완료, 최종 APK 설치 해시 일치, Activity `Status: ok` 확인. 흰 시작 화면이 지속되어 정상 UI와 Vehicle·NaviHelper 응답은 미확인. 시연 검증 환경으로 사용하지 않음 |
| Google APIs Android 14 / API 34 / Emulator 37.2.13 | Debug APK 설치 및 MainActivity `Status: ok`, 첫 화면 확인. 이어서 System UI ANR 및 시스템 재시작이 관찰되어 전체 시연 환경으로 사용하지 않음 |
| AOSP Android 9 / API 28 / Emulator 35.6.11 / 1280×720 | 최종 서명 Release APK 설치 및 MainActivity `Status: ok`. 실제 UI 시나리오 8개 모두 통과. 78.40초 화면 녹화에서 `DONE`까지 확인 |

## 실제 UI 검증

ADB로 새 UI 계층을 읽고 화면의 버튼을 터치했습니다. 앱의 내부 상태를 강제로 바꾸거나 화면 이미지를 합성하지 않았습니다.

| 시나리오 | 결과 | 확인한 동작 |
|---|---|---|
| 부모님과 병원 방문 전체 여정 | PASS | 계획 → 하차 확인 → 주차장 → 주차 확인 → 처음 정한 장소 → 완료 |
| 유일한 보호자 | PASS | 하차 단계를 생략하고 함께 주차·이동 |
| 도어 열림·닫힘 | PASS | 문이 열리면 진행 차단, 닫힘만으로 자동 완료하지 않음 |
| 차량 연결 실패 | PASS | 두 사용자 확인에 더해 문을 직접 확인해야 진행 가능 |
| 주행 중 입력 제한 | PASS | 복잡한 조건·확인 화면 차단 후 정차 시 복귀 |
| 경로 요청 실패 | PASS | 오류 표시, 재시도 후 경로 단계 진입 |
| 이전 요청의 늦은 응답 | PASS | 재시도 후 이전 응답 1건을 무시한 상태와 현재 경로 유지 |
| 쇼핑몰·휠체어·함께 이동 | PASS | 시설별 만남 장소와 휠체어 이동 유의사항 변경 |

증거: [UI 결과 JSON](../evidence/ui-results.json), [단위 테스트 결과 JSON](../evidence/unit-results.json), [첫 화면](../evidence/01-plan.png), [하차 확인](../evidence/02-exit.png), [재합류](../evidence/04-reunion.png), [완료 화면](../evidence/11-done.png).

UIAutomator가 간헐적으로 접근성 루트를 반환하지 못해 재시도한 기록이 있습니다. 새 XML 생성 성공을 확인한 경우만 사용했고, 오래된 XML을 검증 성공으로 취급하지 않았습니다. 최종 스크립트 종료 코드는 0이며 8개 시나리오가 모두 완료됐습니다.

## 실제 시연 영상

`arrivalcare-demo-78s.mp4`는 Android `screenrecord`로 녹화한 1280×720 H.264 영상입니다. 길이는 **78.40초**이며 음성은 없습니다. 부모님·병원 계획, 도어 열림/닫힘, 두 가지 직접 확인, 주차장 안내, 주차 완료, 재합류를 포함합니다. 마지막 프레임의 ‘도착을 함께 마쳤어요’를 확인했습니다. 배포 시 MP4의 재생 시작 메타데이터만 앞쪽으로 옮겼고 화면 내용과 시간은 편집하지 않았습니다.

[영상 다운로드](https://github.com/junyong290/arrival-care-mvp/releases/download/v0.1.0/arrivalcare-demo-78s.mp4)

## 독립 빌드

[GitHub Actions 실행 43677768808](https://github.com/junyong290/arrival-care-mvp/actions/runs/43677768808)이 성공했습니다. Linux/JDK 17 환경에서 demo 테스트·Lint·Debug 빌드와 Pleos Debug 컴파일을 각각 실행했습니다. CI는 차량 SDK 서비스 연결을 검증하는 작업이 아닙니다.

## SDK 범위

**Pleos 진단 APK의 정상 UI 실행과 Vehicle·NaviHelper 서비스 연결은 검증을 완료하지 못했습니다.** 기본 시연은 검증된 `demo` APK를 사용합니다. 초기 진단 빌드에서 `Car UX manager connected` 로그를 관찰했지만, 이를 Vehicle 또는 NaviHelper 연결 성공으로 취급하지 않습니다.

- Vehicle **2.3.2**, NaviHelper **3.0.5**의 공개 Maven 배포물을 내려받아 실제 함수 서명을 확인하고 컴파일했습니다.
- `pleos` 빌드는 실제 SDK 초기화와 연결 진단, Vehicle 도어 capability·위치 조회, AAOS UX 제한 관찰 코드를 포함합니다.
- NaviHelper는 초기화와 `getDestinationInfo()` 읽기 전용 진단을 시도합니다. 가상 시설에는 실제 좌표를 부여하지 않았으므로 `requestRoute()`를 호출하지 않습니다.
- 두 빌드의 경로와 도착은 시뮬레이션입니다. 실제 내비 경로 탐색, 실내 측위, 실제 차량 테스트, 제조사 인증·승인을 완료한 것으로 표현하지 않습니다.

최종 Pleos 재설치 명령은 90초 응답 제한에 걸렸습니다. 이후 기기에 설치된 `base.apk`의 SHA-256을 직접 읽어 배포 APK와 일치함을 확인했습니다. Activity 실행은 `Status: ok`, 대기 시간 37,146ms를 반환했지만, 이후 화면 캡처는 흰 시작 화면이었고 정상 앱 화면과 SDK 응답을 확인하지 못했습니다. 시스템 로그에서는 `eth0 is not ready`와 CarService·미디어 서비스 예외가 관찰됐습니다. 이 기록만으로 화면 정지의 단일 원인을 확정하지 않습니다. 검증 뒤 개발용 에뮬레이터는 종료했습니다.

[Pleos 관찰 기록](../evidence/pleos-observations.txt)

## 발견한 문제와 조치

1. 첫 에뮬레이터 실행에서 기본 `.android` 폴더가 없어 잠금 파일 생성 오류가 반복됐습니다. 폴더를 생성하고 해당 개발 프로세스만 재시작했습니다.
2. Pleos 부팅 로그에서 `eth0 is not ready`, DDS participant 생성 실패, `CarServiceHelper did not connect`, SystemServer Watchdog 재시작을 확인했습니다. 네트워크 호환 옵션을 시험했으며, 에뮬레이터 서비스 오류와 앱 오류를 구분했습니다.
3. 빌드와 에뮬레이터를 동시에 실행했을 때 Windows 여유 메모리가 약 270MB로 줄었습니다. 개발용 Java 프로세스를 정리하고 Gradle 힙 1GB·단일 worker로 조정해 빌드와 실행을 순서대로 진행했습니다.
4. Android 14 시스템 UI ANR을 관찰해, 최종 핵심 시연은 더 가벼운 API 28 AOSP와 공식 안정판 Emulator 35.6.11을 사용하도록 변경했습니다.
5. UI 자동화에서 새 화면의 접근성 루트를 읽지 못하는 경우를 발견했습니다. 오래된 XML을 성공 결과로 사용하지 않도록 수정했고, 앱의 중복 화면 갱신도 한 번으로 합쳤습니다.
6. 함께 이동 모드의 주차 안내 문구를 바로잡고, 시뮬레이터의 ‘다음 실패’와 ‘다음 응답 지연’을 서로 배타적으로 선택하도록 보완했습니다.
7. Vehicle 조회 실패 뒤 같은 조회의 타임아웃이 도착해 상태를 덮지 않도록 조회 세대 번호를 무효화했습니다. 해당 코드로 Pleos 변형을 다시 빌드했습니다.
8. 초기 Pleos 진단 빌드에서 Car UX 연결 로그 뒤 첫 화면이 표시되지 않는 현상을 관찰했습니다. SDK 대기가 UI를 막는 가능성을 줄이기 위해 Vehicle·NaviHelper IPC를 UI 스레드 밖으로 옮기고 Vehicle 대기열을 8개로 제한했습니다. 수정본의 빌드·서명·설치는 확인했지만, 이 환경에서 정상 화면 표시까지 검증하지는 못했습니다. SDK가 준비되지 않았다는 이유로 demo 신호로 몰래 전환하지 않습니다.

Lint 오류는 없습니다. 고정 가로 방향과 Android 12+ 데이터 추출 규칙에 대한 경고 2개가 남아 있습니다. 현재 앱은 로그인·서버·영구 저장 기능을 사용하지 않으며 백업을 비활성화했습니다. 구형 전체화면 API와 Vehicle capability overload의 deprecation 경고는 후속 개선 대상입니다.

호스트는 Intel i5-1235U, RAM 16GB, Intel Iris Xe입니다. Pleos 공식 문서의 외장 GPU 최소 사양에는 미달합니다. 이것만으로 모든 부팅 오류의 원인이라고 단정하지 않습니다.
