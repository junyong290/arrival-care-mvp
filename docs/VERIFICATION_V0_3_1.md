# 도착케어 v0.3.1 — Pleos 제출 APK 검증

작성일: 2026-11-15. 앱 소스 기준: `8e3cbe612c092ee3ab619502009a8db8118a18d8`.

**결과: 최종 서명 Release APK를 공식 Pleos Connect Emulator 2.1.0에 설치하고, 해시 일치와 핵심 UI 검사 3개를 통과했습니다.** [성공 실행 작업 43721867358](https://github.com/junyong290/arrival-care-mvp/actions/runs/43721867358)의 검사 도구 커밋은 `976d6202e2a60f0e49fda2e1d9abcc343bca7c8a`이며 앱 소스 커밋과 구분합니다. 앞선 실패 작업과 성공 작업에서 같은 APK 바이트를 사용했습니다.

## 제출 파일

| 항목 | 값 |
|---|---|
| 파일 | [`apk/Unrival_도착케어_release.apk`](../apk/Unrival_도착케어_release.apk) |
| 빌드 | `pleosOfflineRelease` / 0.3.1 / versionCode 4 |
| 패키지 | `ai.arrivalcare.app.pleos` |
| 최소 / 대상 API | 28 / 34 |
| 크기 | 8,759,249 bytes |
| SHA-256 | `2c1bfad093e3333dec221d839104190b155af7e7fff5bf32c019d68e743d034e` |
| 서명 | Release APK, `debuggable=false`; 상세 인증서·서명 검사: [메타데이터](../apk/SUBMISSION_APK.json) |
| 지도 | 카카오 네이티브 라이브러리를 포함하지 않는 가상 동선도 |

이 APK는 Pleos 차량 서비스 환경용입니다. 일반 Android 스마트폰·태블릿의 보조 시연에는 [이전 v0.3.0 demoOffline APK](https://github.com/junyong290/arrival-care-mvp/releases/download/v0.3.0/arrivalcare-demo-offline-0.3.0.apk)를 사용합니다. 두 앱은 패키지와 차량 신호 처리 방식이 다릅니다.

## 발견한 문제와 수정

이전 Windows Pleos 진단은 부팅 지연과 최종 APK 설치 시간 초과 때문에 미검증으로 남았습니다. 이 기록은 [v0.3.0 보고서](VERIFICATION_V0_3.md)에 보존합니다.

별도 Linux KVM의 [공식 Pleos 2.1.0 재검사 43718641093](https://github.com/junyong290/arrival-care-mvp/actions/runs/43718641093)에서 v0.3.0 `demoOffline`은 설치된 APK의 SHA-256 일치와 부모님 전체 여정, 직접 하차·주차 확인, 유일 보호자의 하차 생략을 통과했습니다. 이 결과는 demo의 차량·내비 Mock 시연입니다.

같은 검사에서 v0.3.0 `pleosOffline`도 설치 해시가 일치했고, 로그에는 **Car UX 연결·입력 허용, Vehicle SDK의 4개 도어 CLOSED 조회, NaviHelper 서비스 연결**이 기록됐습니다. 그러나 도착케어는 백그라운드에 있었고 OEM 내비 앱이 전면에 표시됐습니다. UI 관찰이 시간 초과됐으므로 전체 여정은 통과로 처리하지 않았습니다. 이를 Car UX 연결 실패나 도어 SDK 미지원으로 단정하지 않습니다.

기존 `PleosProbeNavigationGateway`는 앱 생성 즉시 `NaviHelper.initialize()`를 호출했습니다. v0.3.1은 불필요한 초기화의 부수 효과를 피하도록 실제 내비 모드를 명시적으로 선택했을 때만 초기화합니다. 기본 시연에서는 앱이 NaviHelper 초기화나 차량 위치 조회를 요청하지 않습니다. 시연 모드 복귀 시 구독·연결을 정리하고, 이전 비동기 응답을 무시하며, 백그라운드 자동 재연결을 제한했습니다. 경로 요청의 권한·서비스·검증 좌표·POI·사용자 승인 검사와 차량 UX 안전 잠금은 유지했습니다.

v0.3.1 [첫 실행 작업 43720755421](https://github.com/junyong290/arrival-care-mvp/actions/runs/43720755421)에서도 Car UX 입력 허용과 도어 CLOSED를 확인했으나 OEM 내비가 전면에 표시됐습니다. 이때 기본 시연에서는 NaviHelper 연결 로그가 없었습니다. Activity 기록의 OEM 내비 시작 주체는 `launchedFromPackage=ai.umos.homescreen`, `launchedFromUid=1001000`, 사용자 10이었습니다. 따라서 앱의 NaviHelper 초기화가 전면 전환의 원인이었다고 단정하지 않습니다. Pleos 홈 앱이 내비를 시작한 이유와 내부 타이밍까지 확인한 것은 아닙니다.

후속 검사는 `sys.boot_completed=1`과 안정된 system_server를 확인한 뒤 **120초 동안 같은 PID가 유지되는지 추가 관찰**하고 설치·앱 실행을 시작했습니다. OEM 앱을 강제 종료하거나 앱 상태를 주입하지 않았습니다. 같은 v0.3.1 서명 APK로 전체 여정을 완료했습니다. 성공은 이 안정화 절차를 포함한 환경에서 확인한 결과입니다.

Virtual Console의 차량 브리지 Park 설정은 최종 성공 검사에서도 **HTTP 503**으로 실패해 `verified_parked=false`입니다. 이를 실제 정차 신호 설정 성공으로 기록하지 않습니다. Car UX의 입력 허용 신호와 Virtual Console 브리지 설정 성공은 서로 다른 관찰입니다.

## 빌드와 단위 테스트

[빌드 CI 43720052027](https://github.com/junyong290/arrival-care-mvp/actions/runs/43720052027)에서 네 가지 Release 변형 빌드, 기존 48개 단위 테스트, demo lint가 통과했습니다. Android Studio 프로젝트이며 JDK 17 / Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24 / Android SDK 34를 사용합니다.

| 테스트 | 개수 | 결과 |
|---|---:|---|
| JourneyTest | 22 | 통과 |
| ResponsivePolicyTest | 10 | 통과 |
| ArrivalRecommendationTest | 16 | 통과 |
| 합계 | 48 | 통과 |

빌드 CI에서 생성한 APK와 로컬 인증서로 서명한 제출 APK는 구분합니다. 실제 실행 작업은 위 SHA-256의 최종 서명 APK를 설치하고, 기기 내 `base.apk`의 SHA-256까지 비교합니다. SDK 연결 코드의 컴파일 성공만으로 실제 경로 안내 성공을 주장하지 않습니다.

## v0.3.1 Pleos 실제 실행 검사

대상: 공식 **Pleos Connect Emulator 2.1.0 / Android API 34 / x86_64**, **2560×1440 / 160dpi / RAM 4GB**, 호스트 Android Emulator **35.6.11**, GitHub Actions Ubuntu KVM, `swiftshader_indirect` 소프트웨어 GPU, cold boot. 권장 물리 GPU와 다른 CI 환경임을 구분합니다.

시스템 fingerprint는 `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`입니다. [환경 원본](../evidence/v0.3.1/pleos/environment.json)은 이미지 설치·기동 단계의 기록이고, 실제 부팅 완료와 설치 해시·검사 결과는 후속 [실행 결과](../evidence/v0.3.1/pleos/runtime-result.json)에 있습니다.

| 검사 | 현재 상태 |
|---|---|
| 최종 서명 APK 설치 및 기기 내 해시 일치 | 통과 — `2c1bfad093e3333dec221d839104190b155af7e7fff5bf32c019d68e743d034e` |
| 부모님·별도 보호자: 하차 → 주차 → 재합류 → 완료 | 통과 — `parents_dropoff_parking_reunion_complete` |
| 미확인 하차·주차에서 다음 단계 진행 금지 | 통과 — `no_exit_or_parking_without_explicit_confirmation` |
| 유일 보호자: 하차 생략 → 함께 주차 → 만남 장소 | 통과 — `sole_caregiver_skips_dropoff` |

검사는 실제 UI hierarchy에서 버튼을 찾고 ADB 터치·스크롤로 진행합니다. 앱 상태를 주입하거나 Car UX 잠금을 해제하지 않습니다. SDK 응답을 Mock 성공으로 바꾸지 않습니다. 실패한 검사와 재시도를 성공 기록으로 덮어쓰지 않습니다.

실제 캡처: [도착 계획](../evidence/v0.3.1/pleos/pleos/01-plan.png), [하차 확인 전](../evidence/v0.3.1/pleos/pleos/02-exit-unconfirmed.png), [주차 경로 승인](../evidence/v0.3.1/pleos/pleos/03-parking-approval.png), [재합류](../evidence/v0.3.1/pleos/pleos/04-reunion.png), [전체 여정 완료](../evidence/v0.3.1/pleos/pleos/05-complete.png), [유일 보호자와 함께 이동](../evidence/v0.3.1/pleos/pleos/06-together.png).

[SDK·상태 전환 로그](../evidence/v0.3.1/pleos/pleos/app-logcat.txt)에 Car UX 연결과 `moving=false ready=true`, Vehicle SDK의 `지원 도어 4개 조회: CLOSED`, 부모님 여정 `DONE`, 함께 이동 `REUNION`이 기록됐습니다. 이는 에뮬레이터 플랫폼 서비스의 응답이며 실차 센서 검증은 아닙니다. 문 열림·닫힘 전환을 Virtual Console로 제어해 시험한 것으로 확대하지 않습니다.

CI 증거 artifact: `14165620526`, SHA-256 `2298c1f00a32403e32bfbf3fb870d4d64c2562c2ab5bc80aa4d03b4469bc81a0`.

## 구현·시뮬레이션·남은 검증

| 기능 | 제출 빌드 동작 | 검증 범위 |
|---|---|---|
| 추천·하차·주차·재합류·안전 확인 | Kotlin 상태 머신 | 48개 단위 테스트 / 최종 APK의 Pleos 핵심 UI 검사 3개 통과 |
| 도어 상태 | Pleos Vehicle 2.3.2 조회·이벤트 | 최종 APK에서 플랫폼 SDK의 4개 도어 CLOSED 조회 관찰. 개폐 전환·실차는 미검증 |
| 입력 제한 | 실제 Car UX 서비스 | 최종 APK에서 연결·입력 허용 관찰. 신호 미확인 또는 운전 제한 시 잠금 로직 유지; 이번 검사는 동적 주행 전환 검증이 아님 |
| 시설·D/P/M·접근성·지도 | 가상 시설과 Offline 동선도 | 실제 시설의 검증된 접근성·승하차 정보를 뜻하지 않음 |
| 기본 경로·도착 | 사용자 승인·명시적 도착 버튼을 사용하는 시뮬레이션 | 실제 내비 경로 요청 성공과 구분 |
| 실제 내비 모드 | 명시적 선택 후 NaviHelper 연결, 목적지·권한·승인 검사 | 실제 D/P 좌표·POI 미검증으로 요청 차단. 실제 경로 성공 미검증 |
| 카카오 실제 타일 | 이 제출 APK에 SDK 미포함 | ARM 보조 빌드의 실제 지도 타일 검증은 미완료 |
| 실차·실물 스마트폰 | 별도 환경 | 이번 Pleos 에뮬레이터 검증으로 대체하지 않음 |

이전 v0.3.0의 AOSP 가로 UI 15/15, 세로 CI debug UI 4/4, 67.36초 영상은 당시 바이너리·환경의 결과이며 이 v0.3.1 APK 결과로 옮겨 적지 않습니다. 기존 v0.1.0·v0.2.0·v0.3.0 Release와 첨부 파일은 보존합니다. 공모전 제출 페이지 등록과 메일 발송은 수행하지 않았습니다.
