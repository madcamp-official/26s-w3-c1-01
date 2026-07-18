# 핸드오프 문서 — D 모듈 (Orchestrator)

> 핸즈프리 스마트폰 컨트롤러 / 담당: 상태관리 · 게이트키핑 · 캘리브레이션 · 오버레이 (FR-005 / FR-006 / FR-008)
> 브랜치: `partD` · 상태: **구현 완료, Mock 기반 검증 완료, 실제 A/B/C 통합 대기**

이 문서는 **D를 A/B/C와 통합할 사람**과 **D 코드를 이어받을 사람**을 위한 안내서다.

---

## 1. D가 하는 일 (한눈에)

D는 시스템의 "두뇌"다. 직접 카메라/음성/OS이벤트를 만지지 않고, 다음만 한다:

1. **전역 상태 소유** — `ControllerState`(CALIBRATING/ACTIVE/PAUSED/LOCKED/DRAGGING)를 유일하게 소유·전이
2. **게이트키핑** — B가 보낸 명령이 "지금 상태에서 유효한지" 판정 → 유효하면 C에 실행 명령 전달, 무효면 폐기
3. **캘리브레이션** — A의 얼굴 방향값을 9기준점 수집 → `CalibrationProfile` 생성 → A에 전달
4. **오버레이 렌더** — 상태 + 포인터를 조합해 화면 위에 포인터/인디케이터/피드백 표시
5. **안전장치** — 드래그 30초 자동취소, LOCKED 수동 해제 버튼

> A/B/C는 D의 상태 머신을 **몰라도 된다.** 서로 인터페이스(포트)로만 만난다.

---

## 2. 빌드 · 실행 · 테스트

- **요구**: Android Studio (JBR JDK 17 내장), Android SDK API 34, minSdk 26
- **열기**: Android Studio에서 프로젝트 루트 열기 → Gradle Sync (wrapper/의존성 자동 처리)
- **유닛 테스트**: `com.mobileconductor` 패키지 우클릭 → Run Tests (또는 `gradlew testDebugUnitTest`)
  - 현재 **32개 전부 통과** (게이트 85케이스 전수 포함)
- **앱 실행(디버그)**: `app` 구성으로 에뮬레이터/실기기 실행 → `DebugActivity`가 뜸
  - 17개 명령 버튼으로 명령을 주입(B 흉내)하고, "오버레이 시작/권한"으로 오버레이를 띄워 상태 전이를 눈으로 확인
  - 오버레이는 `SYSTEM_ALERT_WINDOW`(다른 앱 위에 표시) 권한이 필요 — 버튼이 설정 화면으로 유도

> `local.properties`는 machine-specific(SDK 경로)이라 `.gitignore` 처리됨. 각자 로컬에서 Android Studio가 생성.

---

## 3. 인터페이스 계약 (A/B/C ↔ D)

패키지 `com.mobileconductor.core.model` 에 모든 공유 데이터 타입이 있다.

### D가 받는 것 (입력 포트)

| 포트 (interface) | 제공자 | 데이터 | 설명 |
| --- | --- | --- | --- |
| `PointerSource.pointerFrames` | **A** | `PointerFrame(x, y, faceDetected, confidence, timestamp)` | 매 프레임 포인터 좌표(정규화 0~1) |
| `PointerSource.rawFaceOrientation` | **A** | `RawFaceOrientation(yaw, pitch, faceDetected, confidence, timestamp)` | 캘리브레이션용 얼굴 방향 원시값 |
| `VoiceCommandSource.events` | **B** | `VoiceCommandEvent(commandId, confidence, timestamp)` | 정규화된 명령(동의어 처리 완료 상태로) |
| `ExecutionSink.results` | **C** | `ExecutionResult(commandId, success, x, y, errorReason)` | 실행 결과(성공 시 오버레이 클릭 애니메이션) |

### D가 주는 것 (출력)

| 대상 | 데이터 | 설명 |
| --- | --- | --- |
| **C** | `ExecutionSink.execute(ExecutionCommand(commandId, timestamp, payload))` | 게이트 통과한 실행 명령. 제어명령(STOP/LOCK/RESUME/UNLOCK)은 여기로 안 감(상태 전이만) |
| **A** | `CalibrationConsumer.onProfileReady(CalibrationProfile)` | 완성된 캘리브레이션 프로파일 |

### `CommandId` (B가 보낼 17종)

```
TOUCH, BACK,
DRAG_START, DRAG_END, DRAG_CANCEL,
SCROLL_DOWN, SCROLL_UP, SCROLL_DOWN_SMALL, SCROLL_UP_SMALL, SCROLL_DOWN_LARGE, SCROLL_UP_LARGE,
NEXT, PREV,
STOP, RESUME, LOCK, UNLOCK
```

> B는 발화("터치"/"클릭")를 **동의어까지 처리해서** 위 `CommandId`로 정규화한 뒤 보낸다. D는 원문 텍스트를 모른다.

### 상태별 명령 유효성 표 (게이트키핑의 핵심)

| commandId | ACTIVE | PAUSED | LOCKED | DRAGGING |
| --- | --- | --- | --- | --- |
| TOUCH, BACK | ✅ | ❌ | ❌ | ❌ |
| DRAG_START | ✅(→DRAGGING) | ❌ | ❌ | ❌ |
| DRAG_END, DRAG_CANCEL | ❌ | ❌ | ❌ | ✅(→ACTIVE) |
| SCROLL_*, NEXT, PREV | ✅ | ❌ | ❌ | ❌ |
| STOP | ✅(→PAUSED, C전달X) | ❌ | ❌ | ✅ **안전우선: DRAG_CANCEL을 C에 먼저, →PAUSED** |
| RESUME | ❌ | ✅(→ACTIVE) | ❌ | ❌ |
| LOCK | ✅(→LOCKED) | ✅(→LOCKED) | ❌ | ❌ |
| UNLOCK | ❌ | ❌ | ✅(→ACTIVE) | ❌ |

- 유효 조합 19개 / 폐기 66개. 이 표는 `TransitionTable.kt`에 데이터로, `CommandGateTest.kt`에 정답지로 이중 명시되어 있다.
- 제어명령은 OS 이벤트가 아니라 **상태 전이만** 하므로 C로 `ExecutionCommand`를 보내지 않는다.

---

## 4. 통합 방법 (★ A/B/C 담당 필독)

D는 `ConductorDependencies` 인터페이스에만 의존한다. 지금은 `MockConductorDependencies`가 꽂혀 있고, **통합 시 실제 구현체 하나만 만들어 갈아끼우면 끝**이다. `ConductorContainer` 이하 배선은 손대지 않는다.

### 4.1 실제 의존성 묶음 만들기

A/B/C의 실제 구현을 담은 클래스를 하나 작성한다:

```kotlin
class RealConductorDependencies(
    // A/B/C가 각자 만든 구현체를 주입
) : ConductorDependencies {
    override val pointerSource: PointerSource = /* A의 FaceTracker 어댑터 */
    override val voiceCommandSource: VoiceCommandSource = /* B의 VoiceRecognizer 어댑터 */
    override val executionSink: ExecutionSink = /* C의 InputInjector 어댑터 */
    override val calibrationConsumer: CalibrationConsumer = /* A의 프로파일 저장소 */
}
```

각 포트는 인터페이스 4개뿐이다(`orchestrator/port/`). A/B/C는 자기 구현을 이 인터페이스로 감싸기만 하면 된다.

### 4.2 조립 지점 교체

`DebugHarness.kt`(또는 통합용 진입점)에서 Mock을 실제로 교체:

```kotlin
// 개발용
private val deps = MockConductorDependencies()
// 통합용 — 이 한 줄만 바꾼다
private val deps = RealConductorDependencies(...)

private val container = ConductorContainer(deps, scope, initialState = ControllerState.CALIBRATING)
val orchestrator = container.orchestrator
```

> 통합 시 `initialState`는 `CALIBRATING`으로 두는 걸 권장(캘리브레이션 완료 전 ACTIVE 차단). 디버그 편의상 현재는 ACTIVE로 시작.

### 4.3 오버레이 데이터 연결

오버레이 서비스는 `OverlayBus`(프로세스 내 싱글턴)를 구독한다. 통합 진입점에서 Orchestrator의 흐름을 Bus로 흘려보낸다(현재 `DebugActivity`가 하는 방식 그대로):

```kotlin
scope.launch { orchestrator.state.collect { OverlayBus.publishState(it) } }
scope.launch { orchestrator.pointerFrames.collect { OverlayBus.publishPointer(it) } }
scope.launch { orchestrator.executionResults.collect { if (it.success) /* publishClick */ } }
OverlayBus.onManualUnlock = { /* UNLOCK 주입 경로 */ }
// 캘리브레이션 진행 중이면: OverlayBus.publishCalibration(calibrationController.uiState.value)
```

---

## 5. A/B/C와 최종 합의 필요 사항

| 상대 | 항목 |
| --- | --- |
| **A** | 좌표계가 정규화 0~1 맞는지 · `confidence` 임계값 기준 · `RawFaceOrientation`의 yaw/pitch 단위(도 vs 라디안) · `CalibrationProfile` 필드 사용 방식 |
| **B** | `CommandId` 17종이 유효성 표와 1:1 일치하는지 · 사전 변경 시 D의 표 동기화 프로세스 · `confidence` 임계값 필터를 B가 처리하는지 |
| **C** | 드래그 자동취소 타임아웃(기본 30초) 값 확정 · `ExecutionCommand.payload` 키 규약(스크롤 방향/강도, 좌표) · `ExecutionResult.errorReason` 코드 목록 |
| 공통 | 타임스탬프 단위(epoch millis)·기준연도 확인 · `core/model`을 공유 `:contracts` Gradle 모듈로 분리할지 |

> ⚠️ 스펙 forD 3절 JSON 예시의 날짜("2026-...")와 timestamp 예시(`1721270000000` = 실제 2024-07-18)가 불일치. 통합 시 타임스탬프 규약을 명확히 할 것.

---

## 6. 코드 맵

```
core/model/            공유 데이터 계약 (A/B/C/D)
orchestrator/
  port/                A/B/C 경계 인터페이스 4종  ← 통합 시 여기를 구현
  state/               CommandGate(순수 게이트), TransitionTable, StateHolder, GateDecision
  calibration/         CalibrationController(9점 수집/예외), CalibrationPoint, CalibrationUiState
  safety/              DragWatchdog(30초 자동취소)
  Orchestrator.kt      voice→gate→C→상태전이 파이프라인 (+ OrchestratorNotice)
  ConductorDependencies.kt / ConductorContainer.kt   DI 경계 + 조립 지점
overlay/               OverlayVisuals(순수 매핑), OverlayView(Canvas), OverlayService, OverlayBus
mock/                  A/B/C 대역 5종 (+ MockConductorDependencies)
DebugActivity/Harness  개발용 진입점 (통합 시 실제 진입점으로 대체/참고)
```

핵심 로직은 전부 **부작용 없는 순수 함수/유닛 테스트 가능** 형태로 분리되어 있다:
- `CommandGate.evaluate(state, commandId, ts)` → `GateDecision`
- `OverlayVisuals.forState(state)` → 시각 규칙

---

## 7. 알아둘 점 / 한계

- **오버레이 터치**: 창은 평소 터치 통과(FLAG_NOT_TOUCHABLE), LOCKED에서만 터치 가능하게 토글되어 수동 해제 버튼이 동작한다.
- **specialUse 포그라운드 서비스**: Play 스토어 정식 배포 시 사유 심사 필요(개발/실험 빌드엔 무관).
- **캘리브레이션 상태 전이**: `CalibrationController`는 프로파일 생성과 `uiState`만 담당. CALIBRATING↔ACTIVE 상태 전이 자체를 Orchestrator와 언제 연결할지는 통합 시 진입 플로우에서 결정(현재 디버그는 ACTIVE로 바로 시작).
- **검증 범위**: 로직은 유닛 테스트 32개 + 에뮬레이터(Pixel 6/API 34) 육안 확인. 실제 A/B/C 연동 통합 테스트는 상대 구현이 나온 뒤 진행.

---

## 8. 참고 문서

- `spec.md` — 전체 기능 명세
- `spec_forD.md` — D 파트 명세(담당 범위)
- `plan_D.md` — 구현 계획 · Phase별 진행현황(P0~P6) · 검증 현황
