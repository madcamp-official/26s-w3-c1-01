# A/B/C/D 통합 기록

> 브랜치 `integration` (base: `partD`) · 작성 2026-07-20
> **상태: 코드 통합 완료, 빌드/실기기 미검증.** 아래 §5를 반드시 먼저 읽을 것.

---

## 1. 왜 merge가 아니라 이식인가

네 사람이 각자 **독립된 안드로이드 프로젝트**를 만들었다. 하나를 나눠 쓴 게 아니다.

| | 소스 패키지 | UI | compileSdk | 빌드 |
| --- | --- | --- | --- | --- |
| A | `com.madcamp.handsfree` | View | 36 | AGP 9.3 + offline-repo |
| B | `com.madcamp.handsfree` | View | 34 | 기본 |
| C | `com.example.hands_free_controller` | Compose | 36 | version catalog |
| D | `com.mobileconductor` | Compose | 34 | Compose 플러그인 |

`settings.gradle.kts`(rootProject.name 4종) · `app/build.gradle.kts` ·
`AndroidManifest.xml` · `MainActivity.kt` · `strings.xml` · `themes.xml`이 전부 4벌씩
달라서, `git merge`는 충돌만 수십 건 내고 빌드 불가능한 트리를 만든다.

**D를 껍데기로 삼고 A/B/C를 파일 단위로 옮겨 붙였다.** D만 유일하게 조립용 구조
(`orchestrator/port/` 인터페이스 4개 + Mock + DI 경계)를 갖고 있어서, D 핸드오프 §4가
지시한 대로 `MockConductorDependencies` 자리에 실제 구현을 끼우는 것으로 끝났다.

## 2. 확정한 것

### 2.1 소스 패키지는 리네임하지 않았다

Kotlin에서 패키지명과 Android `namespace`는 무관하다. 세 패키지가 한 앱에 공존해도
문제가 없고, 리네임하면 컴파일 에러 수백 개만 나고 얻는 게 없다.

- `namespace` / `applicationId` = `com.madcamp.handsfree` (단일)
- 소스 패키지는 파트별 원본 유지

### 2.2 빌드는 A쪽, Compose는 제거

AGP 9.3.0(A) vs AGP 8.5.2 + Kotlin 2.0.20(D) 충돌 → **A로 확정.**

- AGP 8.5.2는 Gradle 8.9를 요구하는데 이 작업 회선은 136MB 배포판을 받지 못한다.
  9.5.1은 이미 로컬 캐시에 있다.
- **AGP 9는 Kotlin이 내장이라 `org.jetbrains.kotlin.android`를 추가하면 Sync가 죽는다.**
  Compose 컴파일러 플러그인도 같은 문제를 안는다.
- 확인해 보니 Compose 사용처는 D의 `DebugActivity` 한 파일, C의 `MainActivity` +
  `ui/theme`뿐이었다. **셋 다 통합 진입점으로 대체될 파일이라 Compose를 통째로 걷어냈다.**
  D의 오버레이(`OverlayView`)는 원래부터 Canvas View라 영향이 없다.
- Gradle wrapper jar는 partC에만 있어서 거기서 가져왔다.

### 2.3 계약 타입 단일화 (통합 작업의 절반)

같은 이름의 타입을 셋이 각자 정의했고 **필드가 서로 달랐다.** 전부
`com.mobileconductor.core.model` 한 곳으로 합쳤다.

| 타입 | 충돌 내용 | 결정 |
| --- | --- | --- |
| `PointerFrame` | A(6필드) / C(5) / D(5, **인자 순서 다름**) | D 순서 + A의 `lowLight` 추가 |
| `RawFaceOrientation` | A에 `eyeOffsetX/Y` 있음, D엔 없음 | D 순서 + eyeOffset을 기본값으로 뒤에 추가 |
| `CommandId` | C 13종 / D 17종 (C엔 제어 명령 없음) | D의 17종. C는 제어 4종을 `NOT_AN_OS_EVENT`로 거부 |
| `ExecutionResult` | C `executedAt: PointerFrame?` / D `x, y: Float?` | D쪽. C 코드를 x/y로 수정 |
| `VoiceCommandEvent` | B `commandId: String` / D `CommandId` enum | **둘 다 유지**, 어댑터에서 변환(§3) |
| `TrackerError` | A만 있고 D엔 없었음 | core/model로 이동 |
| `Level` | A는 `CalibrationProfile.Level` 중첩 / D는 최상위 | D쪽 |

> ⚠️ **`PointerFrame`의 인자 순서 차이가 가장 위험했다.** 필드가 전부 Float/Long이라
> 위치 인자로 생성하면 컴파일은 통과하고 좌표만 조용히 틀어진다. A/C/D 모두 이름 있는
> 인자를 쓰고 있어서 실제 사고는 없었지만, **앞으로도 위치 인자로 만들지 말 것.**

**B의 `CommandDictionary` 17개 문자열이 D의 `CommandId` enum 이름과 정확히 일치**하는 것을
확인했다. 그래서 변환이 `CommandId.valueOf()` 한 줄이다. 어긋나면 어댑터가 로그를 남긴다.

## 3. 새로 쓴 코드 — `com.madcamp.handsfree.integration`

D가 정의한 포트 4개를 A/B/C로 구현한 어댑터들이다.

| 파일 | 하는 일 |
| --- | --- |
| `FacePointerSource` | A의 FaceTracker Flow → D의 `PointerSource`. 계약을 합쳐서 변환이 없다 |
| `VoiceCommandSourceAdapter` | B의 리스너 콜백 → Flow. String commandId → enum 변환. 수동 UNLOCK 주입 경로 |
| `InputExecutionSink` | D의 `execute()` → C의 콜백 엔진. 결과를 Flow로 되돌림 |
| `TrackerCalibrationConsumer` | D의 완성 프로파일 → A의 `updateProfile()` |
| `RealConductorDependencies` | 위 4개 묶음. **A의 좌표를 C에도 중계한다(§3.1)** |
| `ControllerActivity` | 권한 → 접근성 → 오버레이 → 트래커 → 캘리브레이션 → ACTIVE |

### 3.1 A의 좌표는 두 갈래로 간다

C의 `InputExecutionEngine`은 **자기가 마지막으로 받은 `PointerFrame` 위치**에 터치를
찍는다. D를 거쳐 좌표가 전달되는 구조가 아니라서, `RealConductorDependencies`가
A의 스트림을 C에도 직접 중계한다. **이 배선을 빼면 명령은 전달되는데 좌표가 없어서
전부 `NO_POINTER`로 실패한다** — 증상이 "명령을 알아듣긴 하는데 아무 일도 안 일어남"이라
원인을 찾기 어렵다.

### 3.2 D 코드에 넣은 유일한 변경: `Orchestrator.onCalibrationComplete()`

`TransitionTable`에 **CALIBRATING 규칙이 하나도 없다** — 그 상태에서는 모든 음성 명령이
폐기된다. 의도된 설계지만(FR-006: 보정 전 ACTIVE 진입 차단) 그래서 **음성으로는 그
상태에서 빠져나올 수 없다.** D 핸드오프 §7이 "통합 시 결정"으로 남겨둔 항목이라
명령이 아닌 별도 진입점을 추가했다.

## 4. 버린 것

- D `DebugActivity` / `DebugHarness` (Compose 진입점 → `ControllerActivity`가 대체)
- C `MainActivity` / `ui/theme` / mipmap 아이콘 / `input/PointerFrame·ExecutionCommand·ExecutionResult`
- B `MainActivity` (데모 화면)
- A `debug/` 패키지 전체 (`TrackerDebugActivity`, `PointerOverlayView`, `MockCalibration`)
- D `mock/`은 **남겼다** — 유닛 테스트 32개가 쓴다

## 5. ⚠️ 아직 검증되지 않은 것 — 여기부터가 실제 작업

**빌드도 실행도 해 보지 않았다.** 이 환경에 Android SDK/Gradle이 없다.
Android Studio에서 Sync → 빌드 → 실기기 순으로 확인해야 하고, 순서대로 다음을 본다.

### 5.1 빌드 (Android Studio)

1. Gradle Sync. **AGP 9 + Compose 제거 조합이 실제로 통과하는지가 첫 관문이다.**
2. `gradlew testDebugUnitTest` — D의 유닛 테스트 32개 + B의 사전 테스트가 그대로 통과해야
   한다. 계약 타입을 합치면서 필드를 뒤에 추가만 했으므로 통과가 기대값이다.
3. `app/src/main/assets/face_landmarker.task`(3.7MB)가 있는지 확인. gitignore돼 있어
   클론하면 없고, 없으면 실행 즉시 `MODEL_LOAD_FAILED`가 뜬다.

### 5.2 실기기 — 가장 위험한 순서대로

1. **좌표 미러링 (최우선).** A는 "고개를 사용자 기준 오른쪽으로 → x 증가"로 정했다
   (OPEN_ISSUES #5). **C의 주입 좌표계와 어긋나면 터치가 화면 반대편에 찍힌다.**
   컴파일로는 절대 안 잡히고 실기기에서만 드러난다. 어긋나면 고칠 곳은
   `tracking/HeadPose.kt`의 `YAW_SIGN`/`PITCH_SIGN` **두 상수뿐**이다.
2. **접근성 서비스를 켜기 전에는 모든 명령이 실패한다.** 설정에서 수동으로 켜야 하고,
   증상은 `ACCESSIBILITY_SERVICE_NOT_CONNECTED`다.
3. **캘리브레이션 9점 수집이 실제로 끝나는지.** `CalibrationController`는 Mock 좌표로만
   검증됐다. 실제 얼굴로 `noFaceTimeoutMs`(5초) 안에 표본이 모이는지 확인이 필요하다.
4. **카메라 + 마이크 동시 상시 가동의 발열/fps.** A 단독으로 갤럭시 S7에서 13~16fps였다.
   음성 인식이 같이 돌면 더 떨어진다. NFR 목표선은 15fps / 200ms다.
5. **드래그.** `continueStroke`는 앞 stroke와 스레드 순서를 전제로 이어진다.
   `InputExecutionSink`가 Main 디스패처로 넘기게 해 뒀지만 실기기 확인이 필요하다.

### 5.3 팀에 확인해야 하는 것

D 핸드오프 §5의 "최종 합의 필요" 항목 중 통합 과정에서 **A쪽으로 임의 확정한 것들**이다.
틀렸다면 되돌려야 한다.

| 항목 | 확정한 값 | 근거 |
| --- | --- | --- |
| yaw/pitch 단위 | **도(degree)** | A 구현이 이미 도 단위 |
| 타임스탬프 | **단조 시계**(`elapsedRealtime`) | 벽시계와 섞으면 D의 순서 판단이 틀어진다 |
| `confidence` | **A는 항상 1.0** | MediaPipe가 얼굴별 점수를 주지 않는다. D가 임계값 UI를 계획했다면 `lowLight`를 쓸 것 |
| 9점 보간 | **하지 않음** | A는 min/max 선형 정규화만. D가 A쪽 책임으로 기대했다면 어긋난다 |
| B `confidenceThreshold` | **0.6 유지** | B가 D에 확정을 요청한 값. 실기기 인식률 보고 조정 |
| 드래그 타임아웃 | **30초 유지** | D 기본값 |

`ExecutionCommand.payload` 키 규약은 **결국 쓰지 않았다.** C가 스크롤 강도를
`commandId`(SMALL/LARGE)에서 직접 읽어서 payload가 필요 없었다.

## 6. ⚠️ 미해결 — NFR "온디바이스·오프라인" 미충족

**음성 인식이 지금 구글 서버를 탄다.** NFR은 "온디바이스·오프라인, 음성 원본을
서버로 보내지 않는다"를 요구한다.

`EXTRA_PREFER_OFFLINE`을 켜 봤지만 **검증 기기(갤럭시 S7 / Android 8)에서 음성 인식이
통째로 죽었다.** 한국어 오프라인 모델이 없으면 `SpeechRecognizer`가 에러조차 주지 않고
침묵한다 — 콜백이 하나도 오지 않아 `onError` 기반 폴백도 발동하지 못했다.

현재 상태:

- 기본은 **온라인**(`VoiceRecognitionEngine.PREFER_OFFLINE_BY_DEFAULT = false`)
- 오프라인 폴백 경로와 무응답 감시자(5초)는 그대로 살아 있다
- 기기에 한국어 오프라인 모델이 있는 게 확인되면 상수 하나만 켜면 된다

**팀이 결정해야 하는 항목이다.** 선택지:

1. 설치 가이드에 "오프라인 음성 데이터 다운로드"를 넣고 오프라인을 기본으로 — 사용자에게
   한 단계를 더 요구하는 것이고, 옵션 2(낯선 사용자 유입)에서는 이탈 요인이다
2. 온라인 유지 + NFR 변경을 명시 — 데모 장소 네트워크에 의존하게 된다
3. 기기에 모델이 있으면 오프라인, 없으면 온라인 (현재 코드가 이미 이 구조다.
   다만 모델이 없을 때 5초를 버린다)

지금은 3번의 "없을 때" 경로만 기본으로 쓰는 상태다.

---

## 7. 공개 배포 전 보안 점검 (2026-07-20)

옵션 2는 **낯선 사람에게 APK를 설치시키고 접근성 권한까지 요구한다.** 팀 내부에서만
쓸 때와 위험 수준이 다르다고 보고 전체 코드를 훑었다. 찾은 것과 처리 내역이다.

### 7.1 Firestore 규칙이 전면 개방 상태였다 (가장 심각)

콘솔이 프로젝트 생성 시 넣어준 테스트 모드 규칙이 그대로였다:

```javascript
match /{document=**} { allow read, write: if request.time < timestamp.date(2026, 8, 19); }
```

**만료 전까지 누구에게나 전체 읽기·쓰기를 허용한다.** `app/google-services.json`이
공개 저장소에 있어 프로젝트 ID가 비밀이 아니므로, 앱을 설치하지 않은 사람도
`telemetry_feedback`(사용자가 직접 입력한 자유 텍스트)까지 전부 읽을 수 있었다.

> `google-services.json`을 gitignore로 옮기는 건 대응이 아니다. Firebase 클라이언트
> 설정은 설계상 비밀이 아니고, 유일한 보호막이 보안 규칙이다.

**대응**: 저장소 루트에 `firestore.rules` 추가. 로그인이 없는 앱이라 "쓰기만 허용,
읽기 전면 차단" 모델을 택했다. 데이터 확인은 관리자(콘솔)로만 하며, 관리자는 규칙을
우회하므로 팀이 통계를 보는 데는 지장이 없다.

**이 파일은 기록용이고 적용은 콘솔에서 수동으로 해야 한다.** 규칙을 바꿀 때
`firestore.rules`도 같이 고쳐야 저장소와 실제가 어긋나지 않는다.

### 7.2 인식 실패한 발화 전문이 로그에 남고 있었다

`VoiceCommandSourceAdapter.onUnrecognizedSpeech`가 인식된 문장을 그대로 찍었다.
마이크가 상시 켜져 있는 앱이라 **여기 들어오는 건 명령어가 아닌 모든 소리다** —
혼잣말, 옆 사람 대화, TV 소리. 릴리스 빌드가 minify 없이 로그를 전부 유지하므로
배포본에서도 그대로 남는다.

NFR("음성 원본은 저장하지 않는다")과 앱이 화면에서 하는 고지("수집하지 않음: 음성
녹음")를 동시에 깨는 항목이었다. **글자 수만 남기도록 고쳤다.**

### 7.3 접근성 서비스가 안 쓰는 이벤트를 전부 구독하고 있었다

`accessibilityEventTypes="typeAllMask"`인데 `onAccessibilityEvent`는 비어 있었다.
`canRetrieveWindowContent`가 없어 실제 유출은 아니지만, 다른 앱의
`TYPE_VIEW_TEXT_CHANGED`(사용자가 타이핑하는 내용)까지 프로세스로 받고 있었다.
**속성을 통째로 제거했다** — 제스처 주입과 이벤트 구독은 별개라 기능은 그대로다.
스토어 접근성 심사에서도 반드시 걸리는 항목이다.

처음에 `typeNone`으로 바꿨다가 **빌드가 깨졌다.** 이 속성은 flags라서 "없음"을 뜻하는
값이 아예 없다(`'typeNone' is incompatible with attribute accessibilityEventTypes`).
**속성을 생략하는 것이 유일한 방법이고 기본값이 0이다.** 비어 있는 게 실수로 보여서
누가 다시 채우기 쉬운 자리라 xml에 주석을 달아 뒀다.

### 7.4 `sessionId`가 세션이 아니라 영구 설치 식별자였다

SharedPreferences에 한 번 만들고 앱 삭제 전까지 유지됐다. 기기 모델·Android 버전과
묶이면 한 사람의 전 사용 이력이 하나로 연결돼, 앱이 화면에서 약속한 "**익명** 진단
데이터"가 성립하지 않는다.

처음에는 프로세스 수명 단위로 바꿨는데, **그러면 팀원이 만든 고유 사용자 집계가
깨진다.** `telemetry_users` / `totalUserCount`가 `sessionId`를 사용자 ID로 쓰고 있어서,
실행할 때마다 신규 사용자가 되어 수치가 부푼다. 그런데 "공개 경로로 유입된 실제
사용자 수"는 옵션 2에서 가장 중요한 지표라 버릴 수 없다.

**그래서 지표를 포기하는 대신 사실대로 말하는 쪽을 택했다:**

- `installId` — 설치 단위 영구 식별자(기존 값을 그대로 승계. 키를 바꾸면 기존 설치가
  전부 신규로 잡혀 한 번 부푼다). 사용자 집계는 이걸로 한다
- `sessionId` — 앱 실행 단위, 저장하지 않음. 한 번의 사용 흐름을 묶는 용도
- 화면 문구에서 **"익명"을 뺐고**, 설치마다 무작위 식별자를 만들어 보낸다는 사실과
  앱을 지우면 사라진다는 점을 명시했다

이건 익명이 아니라 가명(pseudonymous)이다. 문제는 식별자의 존재가 아니라
**이름과 고지가 실제와 달랐던 것**이었다.

### 7.5 릴리스 빌드의 로그 — 고치지 않기로 했다

`isMinifyEnabled = false`라 모든 `Log` 호출이 배포본에 들어간다. 7.2를 고친 뒤
남는 것은 좌표·각도·타이밍뿐이라 민감 정보가 아니다.

R8을 켜면 MediaPipe/Firebase의 리플렉션 사용처에서 새 버그가 날 수 있고, **공개
직전에 그 위험을 지는 것이 로그를 지워 얻는 이득보다 크다고 판단했다.** 남은 항목으로
적어 둔다.

### 7.6 확인했고 문제 없는 것

- `allowBackup="false"` — 캘리브레이션·텔레메트리가 클라우드 백업에 올라가지 않는다
- 텔레메트리 기본값 `false`(옵트인) + 수집/미수집 항목을 화면에 고지
- 얼굴 이미지·오디오·화면 내용은 어디에도 저장·전송되지 않는다 (코드로 확인)
- `OverlayService`는 `exported="false"`, 접근성 서비스는 `BIND_ACCESSIBILITY_SERVICE`로 보호
- 네트워크는 Firestore SDK(HTTPS)뿐. 평문 통신 없음
- 서명 키(`*.jks`, `*.keystore`, `keystore.properties`)는 gitignore 처리됨 —
  **배포용 키를 만들면 절대 커밋하지 말 것**

### 7.7 규칙과 팀원 코드가 부딪힌 지점 — `telemetry_users`만 읽기를 연다

`upsertUsersAndOverview()`는 `transaction.get(userRef)`로 **클라이언트에서 읽기를 한다.**
"이 설치가 처음인가"를 확인해야 `totalUserCount`를 한 번만 올릴 수 있기 때문이다.
그래서 "읽기 전면 차단"을 그대로 적용하면 **트랜잭션이 실패해 업로드 전체가 죽는다.**

`telemetry_users`에 한해 `get`(단건)은 열고 `list`(질의·열거)는 막았다. 문서 ID가
`installId`(UUID)라서 **자기 UUID를 이미 아는 클라이언트만 자기 문서를 읽을 수 있고**
남의 것을 훑을 수는 없다. 이 구분을 안 하면 전체 사용자 목록이 그대로 노출된다.

**새 컬렉션을 추가하면 `firestore.rules`에도 규칙을 같이 넣어야 한다.** 맨 아래
`match /{document=**} { allow read, write: if false; }`가 나머지를 전부 막기 때문에,
규칙을 빠뜨리면 그 기능만 조용히 실패한다.
