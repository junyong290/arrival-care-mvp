# HMG AppDev Challenge 제출 안내 — Unrival / 도착케어

## 제출 페이지 입력

| 입력 항목 | 값 |
|---|---|
| 팀명 | Unrival |
| 앱 이름 | 도착케어 |
| 한 줄 소개 | 동승자의 상황에 맞춰 하차·주차·재합류를 하나의 여정으로 연결하는 차량용 도착 관리 앱 |
| GitHub 저장소 주소 | https://github.com/junyong290/arrival-care-mvp |
| APK 파일 경로 | `apk/Unrival_도착케어_release.apk` |
| 제출 버전 | 0.3.0 / versionCode 3 / 서명된 release APK |
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
- 실제 실행 화면과 테스트 증거: [`evidence/v0.3.0/`](../evidence/v0.3.0/).
- 보조 ARM·Pleos 빌드와 시연 영상: [기존 v0.3.0 Release](https://github.com/junyong290/arrival-care-mvp/releases/tag/v0.3.0).

## 실행 범위

제출 APK는 실행 검증을 마친 **Offline 시뮬레이션 MVP**입니다. 배포된 `arrivalcare-demo-offline-0.3.0.apk`의 바이트를 바꾸지 않고 파일명만 제출 형식에 맞췄습니다. 실제 카카오 지도 타일, 실제 차량·Pleos 서비스 연동을 검증 완료로 표현하지 않습니다. 자세한 결과는 [검증 보고서](VERIFICATION_V0_3.md)에 있습니다.

이 문서는 첨부된 GitHub 저장소 제출 안내의 필수 항목을 기준으로 작성했습니다.

## 공개 상태와 접수

저장소는 준비 시점인 **2026-11-15에 Public**임을 확인했습니다. 제공된 제출 안내에 따라 **2026-11-23까지 공개 상태를 유지해야 합니다.** 현재 Public이므로 Private 저장소를 11월 16일까지 전환하는 절차는 필요하지 않습니다.

제출 페이지 등록과 접수 메일 발송은 제출자가 진행합니다. 저장소 준비·업로드를 실제 접수 완료로 표시하지 않습니다. 기존 v0.1.0·v0.2.0·v0.3.0 Release와 해당 태그·첨부 파일은 변경하지 않습니다.
