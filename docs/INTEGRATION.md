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
