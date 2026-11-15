# HMG AppDev Challenge 제출 안내 — Unrival / 도착케어

## 제출 페이지 입력

| 입력 항목 | 값 |
|---|---|
| 팀명 | Unrival |
| 앱 이름 | 도착케어 |
| 한 줄 소개 | 동승자의 상황에 맞춰 하차·주차·재합류를 하나의 여정으로 연결하는 차량용 도착 관리 앱 |
| GitHub 저장소 주소 | https://github.com/junyong290/arrival-care-mvp |
| APK 파일 경로 | `apk/Unrival_도착케어_release.apk` |
| 제출 버전 | 0.3.1 / versionCode 4 / 서명된 `pleosOfflineRelease` APK |
| Android 패키지 | `ai.arrivalcare.app.pleos` |
| 실행 환경 | Pleos Connect Emulator 2.1.0 / API 34 / x86_64 |
| 실행 방법·테스트 환경 | [README](../README.md) |

접수 메일에는 **저장소 주소와 최종 제출 커밋의 40자리 hash**를 함께 기재합니다. `apk/`와 제출용 README가 포함된 새 커밋을 사용하며, 이전 v0.3.0 Release 태그의 커밋을 최종 제출 커밋으로 대신 적지 않습니다.

```text
git rev-parse HEAD
```

최종 제출 커밋이 확정되면 APK 링크도 그 커밋에 고정해 전달합니다. 심사 기준은 접수 메일에 기재한 커밋입니다.

## 포함한 제출 파일

- 프로젝트 소스: 저장소 루트의 Gradle 프로젝트와 `app/`.
- 제출 APK: [`apk/Unrival_도착케어_release.apk`](../apk/Unrival_도착케어_release.apk).
- APK 메타데이터·서명·SHA-256: [`apk/SUBMISSION_APK.json`](../apk/SUBMISSION_APK.json).
- 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·필요 설정, Pleos 버전·화면 규격, APK 경로: [README](../README.md).
- 새 Pleos 검증 보고서: [`docs/VERIFICATION_V0_3_1.md`](VERIFICATION_V0_3_1.md).
- 새 실제 실행 검사와 증거: [v0.3.1 Pleos CI](https://github.com/junyong290/arrival-care-mvp/actions/runs/43721867358), [`evidence/v0.3.1/pleos/`](../evidence/v0.3.1/pleos/).
- 이전 일반 Android 시연 화면·테스트: [`evidence/v0.3.0/`](../evidence/v0.3.0/).
- 일반 Android용 보조 앱과 이전 AOSP 시연 영상: [기존 v0.3.0 Release](https://github.com/junyong290/arrival-care-mvp/releases/tag/v0.3.0).

## 실행 범위

제출 대상은 **공식 Pleos 2.1.0 에뮬레이터에서 실행 검증한 v0.3.1 Pleos용 Offline MVP**입니다. 기본 시연 모드에서 불필요한 SDK 초기화를 하지 않고, 사용자가 실제 내비 모드를 선택했을 때만 NaviHelper를 연결하도록 변경했습니다. Vehicle SDK와 Car UX 서비스는 실제 Pleos 플랫폼을 사용하고, 지도·시설·경로·도착은 명시된 시뮬레이션입니다. 일반 Android용 `demo` APK와 구분합니다.

**최종 서명 APK의 설치 해시 일치와 핵심 UI 검사 3개를 통과했습니다.** 부모님 하차·주차·재합류·완료, 직접 확인 전 진행 금지, 유일 보호자의 하차 생략을 확인했습니다. 초기 검사에서 Pleos 홈 앱이 실행한 OEM 내비가 전면에 표시된 기록과, 부팅 완료 후 120초 안정화를 거친 성공 결과를 [v0.3.1 검증 보고서](VERIFICATION_V0_3_1.md)에 구분합니다. 실제 카카오 지도 타일·실차·실제 목적지 경로 안내는 별도 검증 범위입니다.

최종 커밋을 전달받으면 이전 제출 정보의 `a30a837582dec1dc8697411a3f7fd422955e4e1c`를 새 커밋으로 교체합니다. 이 문서에 자기 자신이 포함된 커밋 hash를 고정하지 않고, 저장소 밖 제출 정보·메일 본문에서 최종값을 제공합니다.

이 문서는 첨부된 GitHub 저장소 제출 안내의 필수 항목을 기준으로 작성했습니다.

## 공개 상태와 접수

저장소는 준비 시점인 **2026-11-15에 Public**임을 확인했습니다. 제공된 제출 안내에 따라 **2026-11-23까지 공개 상태를 유지해야 합니다.** 현재 Public이므로 Private 저장소를 11월 16일까지 전환하는 절차는 필요하지 않습니다.

제출 페이지 등록과 접수 메일 발송은 제출자가 진행합니다. 저장소 준비·업로드를 실제 접수 완료로 표시하지 않습니다. 기존 v0.1.0·v0.2.0·v0.3.0 Release와 해당 태그·첨부 파일은 변경하지 않습니다.
