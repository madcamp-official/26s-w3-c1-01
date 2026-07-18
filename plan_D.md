# 구현 계획 [D] — Orchestrator (상태관리 · 게이트키핑 · 캘리브레이션 · 오버레이)

- 담당 FR: FR-005(일시정지/재개), FR-006(캘리브레이션), FR-008(상태관리/오버레이) + 명령 유효성 게이트키핑
- 기술 스택: **Android 네이티브 (Kotlin)** / Coroutines + Flow / Jetpack Compose 오버레이
- 원칙: A/B/C의 실제 구현을 기다리지 않고 **Mock으로 D 전체를 먼저 완성**한다(명세 7절).

---

## 1. 아키텍처 개요

```
        A(PointerFrame/RawFaceOrientation)   B(VoiceCommandEvent)   C(ExecutionResult)
                     │                              │                      │
                     ▼                              ▼                      ▼
        ┌─────────────────────────────── Orchestrator ───────────────────────────────┐
        │  StateHolder (ControllerState 유일 소유)                                     │
        │  CommandGate (순수 함수: (state, commandId) → GateDecision)  ← D의 심장       │
        │  CalibrationController (9점 수집 → CalibrationProfile)                        │
        │  SafetyWatchdog (드래그 30초 자동취소 타이머)                                 │
        └──────────────┬───────────────────────────────────┬──────────────────────────┘
                       │ ExecutionCommand                   │ CalibrationProfile
                       ▼                                    ▼
                       C                                    A
                       │
        OverlayService (Compose): ControllerState + PointerFrame 구독 → 화면 렌더
```

핵심 설계 결정:
- **상태는 `StateHolder` 하나만 소유**. 외부에는 읽기 전용(`StateFlow<ControllerState>`)으로만 노출(디버깅/오버레이용). A/B/C는 상태를 몰라도 됨(명세 5.6).
- **게이트키핑 로직은 부작용 없는 순수 함수**로 분리 → 5×17 = 85 케이스를 유닛 테스트로 완전 커버(명세 8절 DoD).
- A/B/C 경계는 전부 **interface + Flow**로 정의 → Mock/실제 구현 교체가 자유로움.

---

## 2. 프로젝트 구조 (D 소유 패키지)

```
app/src/main/java/com/mobileconductor/
├─ core/model/            # 전 모듈 공유 데이터 계약 (A/B/C/D 합의 스키마)
│   ├─ ControllerState.kt
│   ├─ CommandId.kt
│   ├─ PointerFrame.kt            # A → D
│   ├─ RawFaceOrientation.kt      # A → D (캘리브레이션 중)
│   ├─ VoiceCommandEvent.kt       # B → D
│   ├─ ExecutionCommand.kt        # D → C
│   ├─ ExecutionResult.kt         # C → D
│   └─ CalibrationProfile.kt      # D → A
├─ orchestrator/          # D 본체
│   ├─ port/              # A/B/C 경계 인터페이스 (의존성 역전)
│   │   ├─ PointerSource.kt       # Flow<PointerFrame>, Flow<RawFaceOrientation>
│   │   ├─ VoiceCommandSource.kt  # Flow<VoiceCommandEvent>
│   │   ├─ ExecutionSink.kt       # suspend fun execute(ExecutionCommand)
│   │   └─ CalibrationConsumer.kt # fun onProfileReady(CalibrationProfile)
│   ├─ state/
│   │   ├─ StateHolder.kt         # MutableStateFlow<ControllerState> 소유
│   │   ├─ CommandGate.kt         # ★ 순수 게이트 함수 + GateDecision
│   │   └─ TransitionTable.kt     # 유효성 표를 데이터로 표현
│   ├─ calibration/
│   │   ├─ CalibrationController.kt
│   │   └─ CalibrationStep.kt     # 9 기준점 정의 + 진행상태
│   ├─ safety/
│   │   └─ DragWatchdog.kt        # 30초 자동 취소
│   └─ Orchestrator.kt            # 위 컴포넌트 배선 + Flow 파이프라인
├─ overlay/               # FR-008 오버레이 UI
│   ├─ OverlayService.kt          # WindowManager TYPE_APPLICATION_OVERLAY
│   ├─ OverlayContent.kt          # Compose: 포인터/인디케이터/캘리브 UI/해제버튼
│   └─ OverlayViewModel.kt
└─ mock/                  # 병렬 개발용 (명세 7절)
    ├─ MockPointerSource.kt       # 고정 좌표 반복
    ├─ MockVoiceCommandSource.kt  # 화면 디버그 버튼 → 임의 commandId 주입
    └─ MockExecutionSink.kt       # 항상 success:true

app/src/test/java/...     # 순수 로직 유닛 테스트 (JVM)
app/src/androidTest/...   # 오버레이/서비스 인스트루먼트 테스트
```

> `core/model`은 A/B/C와 공유하는 계약이므로, 통합 시 별도 Gradle 모듈(`:contracts`)로 분리하는 것을 권장. 초기엔 app 내부 패키지로 시작해도 무방.

---

## 3. 핵심 데이터 계약 (Kotlin)

```kotlin
enum class ControllerState { CALIBRATING, ACTIVE, PAUSED, LOCKED, DRAGGING }

enum class CommandId {
    TOUCH, BACK,
    DRAG_START, DRAG_END, DRAG_CANCEL,
    SCROLL_DOWN, SCROLL_UP,
    SCROLL_DOWN_SMALL, SCROLL_UP_SMALL,
    SCROLL_DOWN_LARGE, SCROLL_UP_LARGE,
    NEXT, PREV,
    STOP, RESUME, LOCK, UNLOCK
}   // 총 17개 → 5 상태 × 17 = 85 케이스

// A → D
data class PointerFrame(val x: Float, val y: Float,          // 정규화 0~1
                        val faceDetected: Boolean, val confidence: Float, val timestamp: Long)
data class RawFaceOrientation(val yaw: Float, val pitch: Float,
                        val faceDetected: Boolean, val confidence: Float, val timestamp: Long)

// B → D  (B가 이미 동의어 → commandId 정규화 완료해서 전달)
data class VoiceCommandEvent(val commandId: CommandId, val confidence: Float, val timestamp: Long)

// D → C
data class ExecutionCommand(val commandId: CommandId, val timestamp: Long,
                            val payload: Map<String, Any> = emptyMap())  // 스크롤 방향/강도 등

// C → D
data class ExecutionResult(val commandId: CommandId, val success: Boolean,
                           val x: Float?, val y: Float?, val errorReason: String?)

// D → A  (명세 3절 스키마)
data class CalibrationProfile(
    val profileId: String,
    val referencePoints: List<FaceOrientationValue>,   // 9개
    val faceRangeYawMin: Float, val faceRangeYawMax: Float,
    val faceRangePitchMin: Float, val faceRangePitchMax: Float,
    val sensitivityLevel: Level, val smoothingLevel: Level,  // LOW/MID/HIGH
    val createdAt: String, val updatedAt: String
)
```

---

## 4. 상태머신 & 게이트키핑 (★ D의 심장, 명세 2절)

유효성 표를 **데이터로** 표현하고, 게이트는 그 데이터를 조회하는 순수 함수로 구현.

```kotlin
sealed interface GateDecision {
    // 실행할 OS 명령(execution)이 있을 수도, 상태만 바뀔 수도 있음
    data class Accept(val nextState: ControllerState, val execution: ExecutionCommand?) : GateDecision
    data class Reject(val reason: RejectReason) : GateDecision
}
enum class RejectReason { INVALID_IN_STATE, NEED_UNLOCK, DRAG_IN_PROGRESS }

object CommandGate {
    fun evaluate(state: ControllerState, cmd: CommandId, now: Long): GateDecision { ... }
}
```

전이/실행 규칙(명세 2절 표 + 6절 고유 로직):

| commandId | ACTIVE | PAUSED | LOCKED | DRAGGING |
| --- | --- | --- | --- | --- |
| TOUCH, BACK | Accept(→ACTIVE, exec) | Reject | Reject | Reject |
| DRAG_START | Accept(→DRAGGING, exec) | Reject | Reject | Reject |
| DRAG_END | Reject | Reject | Reject | Accept(→ACTIVE, exec) |
| DRAG_CANCEL | Reject | Reject | Reject | Accept(→ACTIVE, exec) |
| SCROLL_*/NEXT/PREV | Accept(→ACTIVE, exec) | Reject | Reject | Reject |
| STOP | Accept(→PAUSED, **없음**) | Reject | Reject | **Accept(→PAUSED, exec=DRAG_CANCEL)** ← 안전 우선 |
| RESUME | Reject | Accept(→ACTIVE, 없음) | Reject | Reject |
| LOCK | Accept(→LOCKED, 없음) | Accept(→LOCKED, 없음) | Reject | Reject |
| UNLOCK | Reject | Reject | Accept(→ACTIVE, 없음) | Reject |

특수 케이스 3가지(반드시 테스트로 못박기):
1. **STOP/LOCK/RESUME/UNLOCK 등 제어 명령은 C에 ExecutionCommand를 보내지 않음** — 상태 전이만. (OS 이벤트 아님)
2. **DRAGGING + STOP = 안전 우선**: `DRAG_CANCEL`을 C에 먼저 내리고 → PAUSED로 전이(명세 6절, NFR 안전장치).
3. **LOCKED에서 UNLOCK 외 전부 폐기** + "잠금 해제가 필요합니다" 안내(RejectReason.NEED_UNLOCK).

Orchestrator의 명령 처리 파이프라인:
```kotlin
voiceSource.events.collect { ev ->
    when (val d = CommandGate.evaluate(stateHolder.value, ev.commandId, now())) {
        is GateDecision.Accept -> {
            d.execution?.let { executionSink.execute(it) }   // C로
            stateHolder.update(d.nextState)                    // 상태 전이
            if (d.nextState == DRAGGING) dragWatchdog.start() else dragWatchdog.cancel()
        }
        is GateDecision.Reject -> overlay.showHint(d.reason)   // 안내만
    }
}
```

---

## 5. 캘리브레이션 플로우 (FR-006, 명세 3절)

- 진입: 앱 최초 실행 / 프로파일 없음 / 재보정 요청 → `ControllerState = CALIBRATING`, ACTIVE 진입 차단.
- 9 기준점(중앙 + 상/하/좌/우 + 대각선 4) 순차 진행. 각 점에서 A의 `RawFaceOrientation`을 **일정 시간(예: 1.5초) 평균**하여 기록.
- 9점 수집 후 min/max로 `faceRangeYaw/Pitch` 산출 → 5단계에서 민감도/스무딩 선택 → `CalibrationProfile` 완성 → A에게 전달(`CalibrationConsumer.onProfileReady`) → CALIBRATING→ACTIVE.

예외 처리:
| 상황 | 처리 |
| --- | --- |
| 기준점에서 `faceDetected:false` 5초 지속 | 해당 단계 재시도, **3회 실패 시 처음부터** 재시작 |
| 중도 이탈 | 임시 저장 없이 폐기 |
| 재보정 | 기존 프로파일로 계속 조작 유지 → 완료 시점에 교체(원자적 스왑) |

상태 모델(`CalibrationController` 내부):
```kotlin
data class CalibrationUiState(
    val stepIndex: Int,            // 0..8
    val targetPoint: ScreenPoint,  // 현재 하이라이트할 기준점
    val progress: Float,           // 0f..1f
    val retryCountForStep: Int,
    val phase: Phase               // ALIGNING, COLLECTING, ADJUSTING, DONE, FAILED
)
```

---

## 6. 오버레이 UI & 피드백 (FR-008, 명세 4절)

- `OverlayService`(foreground service) + `WindowManager` `TYPE_APPLICATION_OVERLAY`로 전체화면 위 레이어. Compose로 그림.
- `combine(stateFlow, pointerFrameFlow)`를 구독해 렌더.

| 상태 | 포인터 | 인디케이터 |
| --- | --- | --- |
| ACTIVE | 실시간 이동, 기본색 | 초록 |
| PAUSED | 마지막 위치 고정, 반투명 | 노랑 + "일시정지" |
| LOCKED | 숨김/매우 반투명 | 회색 + "잠김" + **수동 해제 버튼(상시 노출)** |
| DRAGGING | 이동 중, 테두리 강조 | 파랑 + "드래그 중" |
| CALIBRATING | 기준점 전용 UI | 진행률 |

- `ExecutionResult.success==true` 수신 시 해당 좌표에 짧은 클릭 애니메이션.
- LOCKED 수동 해제 버튼(물리 터치)은 음성 UNLOCK 실패 대비 **항상** 노출 → 탭 시 UNLOCK 경로로 전이.
- 필요 권한: `SYSTEM_ALERT_WINDOW`(오버레이), foreground service. 실제 접근성 주입은 C 책임이므로 D는 표시만.

---

## 7. Mock 기반 병렬 개발 (명세 7절)

| Port | Mock 동작 |
| --- | --- |
| `PointerSource` | 고정 좌표를 일정 주기로 emit(원 궤도 옵션). 캘리브 중엔 더미 `RawFaceOrientation` |
| `VoiceCommandSource` | 오버레이에 디버그 패널(17개 버튼) → 탭 시 해당 `commandId` 주입 |
| `ExecutionSink` | 항상 `success:true` 로깅 후 `ExecutionResult` 되돌림 |

DI로 Mock ↔ 실제(A/B/C) 교체. 통합 전까지 D는 Mock만으로 전 기능 시연 가능.

---

## 8. 테스트 전략

**JVM 유닛 테스트 (핵심, DoD 1·2)**
- `CommandGateTest`: **5 상태 × 17 commandId = 85 케이스 전수**. 각 케이스의 Accept/Reject·nextState·execution 유무를 표와 대조.
- 특수 케이스 파라미터라이즈드 테스트: STOP-in-DRAGGING이 DRAG_CANCEL을 실행하고 PAUSED로 가는지, 제어명령이 execution 없이 전이만 하는지.
- `CalibrationControllerTest`: 더미 `RawFaceOrientation` 스트림 → 9단계 완료 → 스키마대로 `CalibrationProfile` 생성. 5초 미검출→재시도, 3회 실패→재시작.
- `DragWatchdogTest`: 가상 시계(`TestCoroutineScheduler`)로 30초 경과 시 DRAG_CANCEL 발생.

**인스트루먼트 테스트 (DoD 4·6)**
- 5개 상태별 오버레이 렌더 스냅샷, LOCKED 수동 해제 버튼 노출/동작.

---

## 9. 검증 현황

- 유닛 테스트 31개 전부 통과 (`CommandGateTest` 85케이스, `StateHolderTest`, `OrchestratorTest` 8종, `CalibrationControllerTest` 4종, `DragWatchdogTest` 3종, `OverlayVisualsTest` 6종).
- 에뮬레이터(Pixel 6, API 34) 실기기 육안 확인 완료: 5개 상태 오버레이 렌더링, LOCKED 수동 해제 버튼 동작, DRAGGING 30초 자동취소, TOUCH 클릭 애니메이션까지 전부 확인.
- 확인 과정에서 실제 버그 2건 발견 및 수정:
  1. 캘리브레이션 3회 실패 시 `RESTARTING` 상태가 suspend 없이 즉시 덮어써져 오버레이에 재시작 안내가 전혀 노출되지 않던 문제 → `restartAnnounceDelayMs`(1.5초) 추가.
  2. 클릭 애니메이션이 흰색 테두리라 밝은 배경에서 사실상 보이지 않던 문제 → 굵은 주황색 링으로 변경.

## 10. 구현 단계 (Phase & DoD 매핑)

- [x] **P0. 스캐폴딩** — Android 프로젝트 생성, 패키지 구조, `core/model` 데이터 클래스, port 인터페이스.
- [x] **P1. 상태머신 + 게이트 (DoD 1·2)** — `TransitionTable`, `CommandGate`, `StateHolder`. 85 케이스 유닛 테스트(유효 19 / 폐기 66) 작성, 로직 독립 검증 완료.
- [x] **P2. Orchestrator 배선 + Mock (DoD 2)** — Mock 3종 + Orchestrator 파이프라인, DebugActivity 주입 패널, OrchestratorTest.
- [x] **P3. 캘리브레이션 (DoD 3)** — `CalibrationController` 9단계 수집/평균/범위산출/프로파일 생성 + 5초·3회·재시작 예외. 유닛 테스트 4종.
- [x] **P4. 오버레이 UI (DoD 4)** — `OverlayVisuals`(순수 매핑, 유닛 테스트) + `OverlayView`(Canvas) + `OverlayService`(WindowManager 포그라운드) + `OverlayBus` seam. DebugActivity에서 기동/권한/데이터 publish.
- [x] **P5. 안전장치 (DoD 5·6)** — `DragWatchdog`(30초 자동 `DRAG_CANCEL`) + Orchestrator 연동(Mutex 직렬화, `notices`), LOCKED 수동 해제 버튼→UNLOCK 라우팅. 유닛 테스트(watchdog 3 + 통합 2).
- [~] **P6. 통합 준비** — DI 정리 완료: `ConductorDependencies`(A/B/C 경계 묶음) + `ConductorContainer`(조립 지점) + `MockConductorDependencies`. 통합 시 Mock 자리에 `RealConductorDependencies`만 주입하면 배선 재사용. 스모크 테스트 추가. (남음: 실제 A/B/C 교체 및 통합 테스트 — A/B/C 완성 후 진행)

---

## 11. 통합 시 A/B/C와 확인할 사항 (명세 9절)

- **A**: 좌표계(정규화 0~1)·`confidence` 임계값 기준, `CalibrationProfile` 필드/단위(도 vs 라디안) 최종 합의.
- **B**: `commandId` 사전(위 17종)이 유효성 표와 1:1 일치하는지, 사전 변경 시 동기화 프로세스.
- **C**: 드래그 자동취소 타임아웃(기본 30초), `ExecutionCommand.payload`(스크롤 방향/강도 키), `ExecutionResult.errorReason` 코드 목록 합의.
- 공통: `core/model`을 공유 `:contracts` Gradle 모듈로 분리할지 결정.
```
