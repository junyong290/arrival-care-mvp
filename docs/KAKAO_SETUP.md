# 카카오맵 설정 — v0.3.0

앱 키는 **프로젝트 루트의 `local.properties`**에만 넣습니다. 이 파일과 `signing.properties`는 Git에서 제외되어 있습니다. 키 값을 채팅, README, 커밋에 붙여 넣지 마세요.

## 콘솔에서 등록

1. [Kakao Developers](https://developers.kakao.com/)에 로그인하고 사용할 앱을 선택하거나 생성합니다.
2. **카카오맵 → 사용 설정 → 상태 ON**으로 설정합니다. 추가 앱·쿼터에 관한 유료 설정은 계정 소유자가 내용을 확인하고 결정합니다.
3. **앱 → 플랫폼 키 → 네이티브 앱 키 → Android 앱 정보**에서 아래 패키지와 키 해시를 등록합니다. REST API 키나 JavaScript 키를 사용하지 않습니다.

| 설치할 APK | Android 패키지명 |
|---|---|
| demoKakao Release | `ai.arrivalcare.app.demo` |
| pleosKakao Release | `ai.arrivalcare.app.pleos` |

현재 배포용 서명 인증서의 Kakao SHA-1/Base64 키 해시:

```text
VRrndCnhcmPis6Eo0kxawHoNJzU=
```

이 값은 v0.3.0의 최종 Release APK 네 개의 실제 서명에서 다시 확인했습니다. 기존 v0.2.0과 같은 인증서입니다. **debug APK는 별도의 debug 인증서를 사용**하므로 위 해시와 다릅니다. 앱의 **지도 상태 → 지도 연결**에서 설치된 APK의 실제 패키지명과 해시를 확인·복사할 수 있습니다.

4. 프로젝트 루트 `local.properties`에서 SDK 경로는 유지하고 다음 필드를 채웁니다. 따옴표와 공백을 넣지 않습니다.

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
KAKAO_MAP_NATIVE_KEY=발급받은_네이티브_앱_키
```

5. 같은 서명 설정을 사용해 다시 빌드합니다.

```powershell
.\gradlew.bat :app:assembleDemoKakaoRelease
adb install -r app/build/outputs/apk/demoKakao/release/app-demo-kakao-release.apk
adb shell am start -n ai.arrivalcare.app.demo/ai.arrivalcare.app.MainActivity
```

`local.properties`를 바꾸면 **APK를 다시 빌드·설치**해야 합니다. 이미 설치된 앱에 키가 자동으로 반영되지는 않습니다. CI에는 개인 키를 전송하지 않으므로 CI 산출물은 기본적으로 키 없는 검증용입니다.

## 스마트폰 연결과 검증

1. Android 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드 번호 7회 누르기.
2. 개발자 옵션 → USB 디버깅 켜기.
3. 데이터 전송이 가능한 USB 케이블로 PC에 연결합니다. 휴대폰 화면을 잠금 해제하고 **이 컴퓨터에서 USB 디버깅 허용**을 누릅니다.
4. `adb devices -l`에서 `device` 상태인지 확인합니다. `unauthorized`는 휴대폰의 승인 대기입니다.
5. ARM 기기에서 앱을 열고 **실제 도로·건물명·카카오 지도 저작권 표시가 보이는지** 확인합니다. `onMapReady`만으로 실제 타일 표시 검증을 대신하지 않습니다.
6. D/P/M 상세, 확대·축소·전체 동선, 목적지 변경, 세로·가로 전환, 백그라운드 복귀를 확인합니다.

가상 A병원/B쇼핑몰의 D/P/M과 C 차량 표시는 **실제 지도 위에 얹은 시연 데이터**입니다. 서울아산병원은 공식 대표 위치 H만 표시하며, 미검증 D/P/M은 만들지 않습니다. 실제 지도 타일과 자동차 길찾기는 별개입니다.

## 지원 범위와 문제 해결

- Kakao Android SDK v2 **2.15.2**, 네이티브 ABI **arm64-v8a / armeabi-v7a**. x86_64 Pleos Emulator에는 `pleosOffline` APK를 사용합니다. ARM APK를 x86에 설치할 수 있다고 안내하지 않습니다.
- 키 없음, 지원하지 않는 OpenGL ES/ABI, 초기 연결 지연, SDK 오류는 명시적인 대체 화면으로 전환합니다. 실제 시설의 지도 미연결 화면에는 가짜 도로를 그리지 않습니다.
- **401:** 네이티브 앱 키, 패키지명, 설치된 APK의 실제 서명 해시 확인.
- **403:** 카카오맵 사용 설정과 앱 권한 확인.
- **429:** 요청 한도 확인. 자동으로 유료 설정을 켜지 않습니다.
- **-2/-3/-4/499:** 인터넷 연결 후 지도 상태에서 다시 연결.
- 앱 다크 테마와 샘플 동선도는 야간 색상을 지원합니다. Kakao 타일은 SDK 기본 지도를 사용합니다. 확인되지 않은 야간 지도 스타일 ID를 임의로 지정하지 않습니다.
- SDK 오류 메시지 원문에는 인증 정보가 포함될 수 있어 앱은 오류 코드와 안전한 설명만 표시합니다.

공식 근거: [SDK 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/), [네이티브 앱 키 설정](https://developers.kakao.com/docs/ko/app-setting/app#native-app-key), [카카오맵 사용 설정](https://developers.kakao.com/docs/ko/kakaomap/common).
