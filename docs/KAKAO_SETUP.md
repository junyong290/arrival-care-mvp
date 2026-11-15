# 카카오 지도 연결하기

현재 기본 배포는 **앱 키 미포함** 상태입니다. 키가 없어도 샘플 동선으로 모든 여정을 시연할 수 있지만, 실제 카카오 지도 타일 인증에 성공했다고 표현하지 않습니다.

## 1. 개발자 앱과 네이티브 키

1. [Kakao Developers](https://developers.kakao.com/)에 로그인하고 도착케어에 사용할 앱을 생성하거나 기존 앱을 선택합니다.
2. 해당 앱의 **카카오맵 → 사용 설정 → 상태 ON**을 설정합니다.
3. **앱 → 플랫폼 키 → 네이티브 앱 키**에서 Android 앱 정보를 등록합니다. REST API 키를 지도 SDK에 사용하지 않습니다.
4. 기본 시연 앱의 패키지는 **ai.arrivalcare.app.demo**입니다. Pleos 변형은 **ai.arrivalcare.app.pleos**에 맞는 네이티브 키/등록 정보가 필요합니다.
5. 아래 방법으로 실제 설치 APK의 **키 해시**를 구해 Android 앱 정보에 등록합니다. SHA-256 체크섬이나 APK 파일 해시가 아니라, 서명 인증서 SHA-1을 Base64로 인코딩한 값입니다.

2026-11-15 기준 공식 문서는 계정에서 첫 번째로 카카오맵을 활성화한 앱에 무료 쿼터를 제공하며, 추가 앱이나 무료 한도 초과에는 별도 유료 설정이 필요할 수 있다고 안내합니다. 콘솔의 해당 앱 상태를 확인하세요. 이 프로젝트는 결제나 비즈월렛 설정을 자동으로 변경하지 않습니다.

근거: [카카오맵 사용 방법](https://developers.kakao.com/docs/ko/kakaomap/common), [네이티브 키·패키지·키 해시](https://developers.kakao.com/docs/ko/app-setting/app#native-app-key).

## 2. 키 해시 얻기

실행 중인 앱의 지도 왼쪽 위 **지도 상태** 버튼을 누르면 패키지명과 키 해시가 표시됩니다. **키 해시 복사**로 복사할 수 있습니다.

APK 파일에서도 확인할 수 있습니다. Android SDK와 JDK 17을 설치하고 다음 명령을 실행하세요.

    python tools/kakao_key_hash.py path/to/arrivalcare-demo-kakao-0.2.0.apk --sdk C:/Android/Sdk

이 도구는 APK의 공개 서명 인증서만 읽습니다. 키스토어 비밀번호나 개인 키를 요구하지 않습니다. Debug와 Release는 일반적으로 다른 인증서를 사용하므로 각각 등록해야 합니다.

## 3. 로컬 키 설정과 빌드

Git에서 제외된 프로젝트 루트 **local.properties**에 추가합니다. 기존 sdk.dir은 유지하세요.

    KAKAO_MAP_NATIVE_KEY=YOUR_NATIVE_APP_KEY

동일 이름의 환경 변수 또는 Gradle 속성도 지원합니다. 키 값을 README, 커밋 또는 공개 이슈에 넣지 마세요. 네이티브 클라이언트 키는 APK에 포함되므로 패키지명과 서명 키 해시 등록을 정확히 맞춰야 합니다.

    .\gradlew.bat :app:assembleDemoKakaoDebug
    adb install -r app/build/outputs/apk/demoKakao/debug/app-demo-kakao-debug.apk

Release 서명 설정이 있다면 :app:assembleDemoKakaoRelease를 사용합니다. 실제 ARM 스마트폰에서 인터넷 연결 후 실행하고, **카카오 지도 · 샘플 마커** 표시와 실제 타일을 확인하세요. D/P/M을 누르면 각 상세 정보가 표시됩니다. 인증 실패·네트워크 오류·초기화 시간 초과에는 샘플 동선이 유지됩니다.

## 4. ARM과 Pleos 호환성

SDK **2.15.2** 배포 AAR을 직접 확인했으며, 네이티브 라이브러리는 arm64-v8a와 armeabi-v7a만 포함합니다. 공식 지원 사양은 Android API 23 이상, OpenGL ES 2.0 이상이며 앱 자체는 API 28 이상입니다.

| 환경 | 선택할 변형 |
|---|---|
| 실제 ARM Android 스마트폰 | demoKakao |
| x86/x86_64 에뮬레이터 | demoOffline |
| x86_64 Pleos 2.1.0 에뮬레이터 | pleosOffline 또는 차량 신호까지 시연하는 demoOffline |
| 지원 사양을 갖춘 실제 ARM Pleos 환경 | pleosKakao 컴파일 가능, 실제 차량 검증 별도 필요 |

ARM 전용 APK는 x86 장치에서 설치 자체가 거부될 수 있습니다. 그래서 SDK를 포함하지 않는 호환 APK를 별도로 제공합니다. APK에 ABI 이름을 추가하는 것만으로 카카오 네이티브 라이브러리의 x86 지원이 생기지 않습니다.

## 5. 지도와 내비게이션의 차이

실제 타일은 카카오 SDK가 표시합니다. D/P/M 좌표는 UI 확인용 샘플이며 실제 병원·쇼핑몰의 검증된 하차 지점이 아닙니다. 이 좌표를 NaviHelper나 외부 내비게이션으로 전달하지 않습니다. 실제 위치 추적, 실제 도로 경로 계산, 접근성 검증은 포함하지 않습니다. 카카오 저작권·출처 표시를 덮거나 제거하지 않습니다.

SDK 근거: [요구 사양](https://apis.map.kakao.com/android_v2/docs/getting-started/), [초기화·인증·생명주기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/).
