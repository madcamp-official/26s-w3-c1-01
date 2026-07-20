# 모션 캡쳐(손) 구현 계획 (Implementation Plan) v0.1

> 대상 명세: [MOTION_CAPTURE_SPEC.md](MOTION_CAPTURE_SPEC.md)
> 기반 코드: `integration` 브랜치 · 작성 2026-07-20
> **원칙: D 포트 4개 무변경. A `FaceTracker` 카메라 코드는 Phase 4 전까지 건드리지 않는다.**

---

## 0. 계획의 두 축 (왜 이 순서인가)

실제 배선을 읽고 나온 두 가지 결정이 전체 순서를 정한다.

### 축 1 — 캘리브레이션을 그대로 재사용한다

`CalibrationController`는 `PointerSource.rawFaceOrientation`(yaw/pitch, 도 단위)을 구독해
9점을 수집하고 `faceRangeYaw/Pitch` min/max를 뽑는다
([CalibrationController.kt](../app/src/main/java/com/mobileconductor/orchestrator/calibration/CalibrationController.kt)).

→ **손도 `RawFaceOrientation`에 "정규화된 2축 원시 신호"를 실어 보내면 이 컨트롤러가
그대로 동작한다.** `yaw` 슬롯 = 손의 수평 위치, `pitch` 슬롯 = 수직 위치로 재해석한다.
`HandLandmarkMapper`는 산출된 `faceRangeYaw→x reach`, `faceRangePitch→y reach`를 읽어
정규화한다. **D도 CalibrationController도 한 줄 안 고친다.**

- 트레이드오프: `RawFaceOrientation`/`CalibrationProfile`의 필드 이름이 얼굴 의미로 남는다.
  MVP에서는 감수하고, 이름 중립화는 SPEC §9-A/§9-D의 팀 결정으로 미룬다.

### 축 2 — HYBRID를 뒤로 미뤄 A 리팩터링을 회피한다

`FaceTracker`가 CameraX `bindToLifecycle`을 직접 소유한다
([FaceTracker.kt:100~](../app/src/main/java/com/madcamp/handsfree/tracking/FaceTracker.kt)).
두 트래커가 동시에 카메라를 열면 충돌한다(SPEC §7).

→ **MVP는 모드 배타(FACE **또는** HAND, 동시 아님).** 모드 전환 = 한 트래커 stop +
다른 트래커 start. 이러면 `HandTracker`도 자기 카메라를 열어도 되고, FrameHub도
A `FaceTracker` 수정도 필요 없다. **HYBRID(동시)는 Phase 4로 분리**, 여기서만 FrameHub가 등장한다.

---

## Phase 0 — 사전 결정·준비 (구현 전 필수)

| 할 일 | 산출물 | 막히면 |
| --- | --- | --- |
| SPEC §9의 A/B/C/D/G 결정 확정 | 팀 합의 기록 | §9-B(merge)·§9-C(제스처 세트)는 아래에 **기본값**을 정해 뒀으니 반대 없으면 진행 |
| `hand_landmarker.task` 확보 | `app/src/main/assets/hand_landmarker.task`(~7.5MB) | MediaPipe 공식 배포에서 받는다. gitignore 확인 후 [assets/README.md](../app/src/main/assets/README.md)에 명시 |
| `.gitignore`에 모델 포함 여부 확인 | — | `face_landmarker.task`는 커밋돼 있다(3.7MB). 같은 방식으로 처리 |

**이 계획이 채택하는 §9 기본값**
- §9-A: MVP는 `PointerFrame.faceDetected`를 "검출됨"으로 재사용(개명 안 함).
- §9-B: `GestureCommandSource`가 `VoiceCommandSource`를 구현 → `RealConductorDependencies`에서 merge. **포트 무변경.**
- §9-C: 잠금/해제는 **손 제스처에서 제외**(오작동 위험). 음성 + 상시 노출 버튼으로만. → 정적 제스처에서 `LOCK`/`UNLOCK` 뺀다.
- §9-D: `CalibrationProfile` 재사용(축 1). 별도 프로파일 안 만든다.
- §9-G: HAND는 추가 옵션. 기본 모드는 FACE 유지.

---

## Phase 1 — 손 포인터 (FR-M-001)

목표: HAND 모드에서 **손으로 커서만** 움직인다(명령은 아직 음성). 제스처 없음.

### 1.1 신규 파일

| 파일 | 대응(A) | 내용 |
| --- | --- | --- |
| `tracking/HandTracker.kt` | `FaceTracker` | HandLandmarker LIVE_STREAM + 자기 CameraX 바인딩. 검지끝(#8) → `RawFaceOrientation`(수평→yaw슬롯, 수직→pitch슬롯) + `PointerFrame` 방출 |
| `tracking/HandLandmarkMapper.kt` | `PointerMapper` | reach 프로파일 정규화 → 시선보조/pitch결합 없이 단순화 → `Smoother` 재사용 → 클램핑 |

### 1.2 재사용

- `Smoother`([Smoother.kt](../app/src/main/java/com/madcamp/handsfree/tracking/Smoother.kt)) 그대로.
- `PointerFrame`/`RawFaceOrientation`/`CalibrationProfile` 그대로.
- `ImageProxy.toUprightBitmap()`/`isLowLight()`는 현재 `FaceTracker.kt`에 private로 있다.
  → **공용 `tracking/CameraFrameUtils.kt`로 추출**(A 로직 변경 아님, 위치만 이동). HandTracker도 쓴다.

### 1.3 좌표 규약 (가장 위험, SPEC §11-1)

- 미러링: **손을 사용자 오른쪽으로 → x 증가.** `FaceTracker`와 반드시 일치.
  전면 카메라라 `toUprightBitmap` 뒤 좌우 반전이 이미 들어가는지 실기기로 확인해 부호 상수 하나로 흡수.
- `displayRotationDegrees` 회전 흡수도 FaceTracker의 `applyDisplayRotation` 패턴을 그대로 이식.

### 1.4 배선

`HandPointerSource`(신규, `FacePointerSource`와 대칭)를 만들고, **Phase 3의 모드 스위치가
FACE/HAND 중 하나의 `PointerSource`를 `RealConductorDependencies`에 넘긴다.** Phase 1
단독 검증은 임시로 `RealConductorDependencies`가 `HandTracker`를 쓰게 해서 확인.

### 1.5 Definition of Done
- HAND 모드에서 검지로 커서가 따라오고, 손 놓으면 마지막 위치 고정.
- 캘리브레이션(축 1)으로 reach 범위가 잡혀 화면 끝까지 도달.
- FACE 모드는 전혀 영향 없음(회귀).

---

## Phase 2 — 손 제스처 → 명령 (FR-M-002~004)

목표: 손 모양/동작으로 명령. **여기가 이 기능의 핵심 난이도.**

### 2.1 신규 파일

| 파일 | 내용 |
| --- | --- |
| `tracking/GestureClassifier.kt` | 랜드마크 21점 → 제스처 판정. **순수 함수 로직 → D 스타일 유닛테스트 대상** |
| `tracking/HandGesture.kt` | 제스처 enum(`PINCH`, `FIST`, `OPEN_PALM`, `THUMBS_UP`, `SWIPE_UP/DOWN/LEFT/RIGHT`) |
| `integration/GestureCommandSource.kt` | `VoiceCommandSource` 구현. 제스처 → `CommandId` → `VoiceCommandEvent`. 게이트(§2.3) 내장 |

### 2.2 판정 로직 (GestureClassifier)

- **손가락 폄/접힘**: 각 손가락 끝 랜드마크가 PIP 관절보다 손목에서 먼지로 판정(정적).
- **핀치**: #4(엄지끝)·#8(검지끝) 정규화 거리 < 임계값. 캘리브레이션의 `pinch_threshold` 개인화(SPEC §6-5). MVP는 상수로 시작.
- **스와이프**(동적): 최근 N프레임 검지끝 궤적 버퍼 → 이동량 > 0.15 & 소요 < 500ms → 방향 판정.
- 정적/동적 우선순위: 스와이프 판정 중이면 정적 억제(SPEC §4.4 "제스처 판정 우선").

### 2.3 제스처 게이트 (FR-M-004, 오검출 억제 — 없으면 못 쓴다)

`GestureCommandSource`에 둔다. 음성의 신뢰도 임계값(B 0.6)에 대응.
- 최소 유지 프레임 5(≈0.3s), 쿨다운 800ms, 랜드마크 신뢰도 ≥0.6.
- 애매하면 **미탐 택함**(오탐보다 낫다).

### 2.4 매핑 (Phase 0 §9-C 반영 — 잠금/해제 제외) — **구현 확정본**

| 제스처 | CommandId | 유효 상태(D가 판정) |
| --- | --- | --- |
| 핀치 | `TOUCH` | ACTIVE |
| 주먹 | `DRAG_START` | ACTIVE |
| 손 펴기 | `DRAG_END` | DRAGGING |
| 엄지 척 | `RESUME` | PAUSED |
| 스와이프 상/하 | `SCROLL_UP` / `SCROLL_DOWN` | ACTIVE |
| 스와이프 좌/우 | `PREV` / `NEXT` | ACTIVE |

→ 주먹 이중 매핑(DRAG vs LOCK) 제거로 SPEC §4-2의 ⚠️ 위험이 사라진다.

**구현 시 SPEC §4보다 더 줄인 것(의도적):**
- **`STOP`(손바닥 hold 1s) 제스처 제외.** OPEN_PALM은 `DRAG_END` 하나만 매핑한다. 손바닥
  quick(DRAG_END) vs hold(STOP)의 이중 발화 모호성을 없애고, **정지는 음성 전담**으로 둔다
  (NFR·§9-G: 안전 명령은 손이 안 보여도 걸려야 한다). 정적 제스처는 "유지→1회 발화"라
  hold-누적과 quick-발화를 한 손 모양에 겹치면 반드시 둘 다 튄다 — 그래서 아예 뺐다.
- **`BACK`(좌→우 큰 스윙) 제외** — 좌/우 스와이프(PREV/NEXT)와 충돌. 뒤로가기는 음성 전담.
- **스크롤 강도(SMALL/LARGE) 미구현** — 스와이프는 기본 `SCROLL_UP/DOWN`만. 거리 기반 강도는
  다음 단계 확장으로 미룸(오검출 여유 확보 우선).

결과적으로 손 제스처는 **정적 4종 + 스와이프 4종 = 8종**, 안전/내비 크리티컬은 전부 음성·버튼.

### 2.5 배선 — merge (§9-B)

`RealConductorDependencies`에서:
```kotlin
val gesture = GestureCommandSource(...)          // HandTracker 제스처 이벤트 소비
override val voiceCommandSource =
    MergedCommandSource(voice, gesture)          // 두 Flow를 merge, VoiceCommandSource 구현
```
- `voice` 필드는 유지(파이프라인이 `d.voice.start()`·`d.voice.inject(UNLOCK)`로 직접 참조).
- `Orchestrator`/`ConductorContainer`는 무변경 — `voiceCommandSource` 하나만 본다.
- 선택: `VoiceCommandEvent`에 `source` 필드를 **기본값과 함께 뒤에 추가**하면 오버레이가 음성/제스처 피드백을 구분(INTEGRATION §2.3 규약 준수). 없어도 동작엔 무방.

### 2.6 Definition of Done
- 핀치로 탭, 주먹→손펴기로 드래그, 스와이프로 스크롤/페이지.
- 게이트 기본값으로 실사용 가능한 오검출률.
- 상태 게이트키핑은 기존 `CommandGate`가 그대로 처리(제스처도 음성과 동일 취급).

---

## Phase 3 — 입력 모드 통합 (FR-M-005, HYBRID 제외)

목표: 설정/음성으로 FACE ↔ HAND 전환. **동시 아님(배타).**

### 3.1 신규·수정

| 파일 | 변경 |
| --- | --- |
| `integration/InputModeController.kt` (신규) | 현재 모드 보관. 전환 시 활성 트래커 stop + 대상 start, `RealConductorDependencies` 재배선 |
| `integration/ControllerPipeline.kt` (수정) | `start()`가 모드에 따라 FaceTracker **또는** HandTracker를 기동([현재 84행 `t.start()`](../app/src/main/java/com/madcamp/handsfree/integration/ControllerPipeline.kt)) |
| `integration/ControllerActivity.kt` (수정) | 모드 선택 UI(토글) |
| `voice/CommandDictionary` (선택) | "손 모드"/"얼굴 모드" 발화 추가 시 |

### 3.2 전환 규칙
- 전환 시 **진행 중 드래그 안전 취소**(DRAGGING이면 먼저 DRAG_CANCEL 주입 후 전환).
- **음성 안전 명령("멈춰"/"잠금")은 두 모드 공통 상시 유지**(NFR·SPEC §9-G). 음성 엔진은 모드와 무관하게 항상 켜 둔다 → `d.voice`는 전환과 독립적으로 계속 살아 있게 배선.
- 캘리브레이션은 모드별로 따로 저장(FACE 프로파일 / HAND reach 프로파일). `CalibrationStore` 키 분리 필요 → [CalibrationStore.kt](../app/src/main/java/com/madcamp/handsfree/integration/CalibrationStore.kt) 확장.

### 3.3 Definition of Done
- 앱 안 나가고 FACE↔HAND 전환, 각 모드 캘리브레이션 독립.
- 전환 중 드래그/포인터가 안전하게 처리.
- 기본 부팅 모드 = FACE(회귀 안전).

---

## Phase 4 — HYBRID + FrameHub (선택, A 합의 필요)

> 여기서만 A `FaceTracker`를 건드린다. SPEC §9-E. MVP 범위 밖으로 두고, 성능 여유 확인 후 착수.

### 4.1 리팩터링
- `tracking/FrameHub.kt` 신규: CameraX `ImageAnalysis`를 **한 번만** 바인딩, 프레임을 등록된 소비자(들)에 fan-out.
- `FaceTracker`/`HandTracker`에서 **카메라 바인딩 제거** → `onFrame(bitmap, timestamp)` 콜백만 받는 형태로 변경. detectAsync/onResult 로직은 유지.
- HYBRID: 손 매 프레임 / 얼굴 격 프레임 스킵으로 fps 방어.

### 4.2 중재(SPEC §5.1)
- 손 검출 시 손 우선, 미검출 시 얼굴 폴백. 전환 프레임 좌표 램프(점프 방지).

### 4.3 DoD
- HYBRID에서 15fps 유지(§9-F 실측). 안 되면 HYBRID 미출시, Phase 3까지가 제품.

---

## 검증 순서 (SPEC §11 매핑)

| # | 검증 | Phase |
| --- | --- | --- |
| 1 | `hand_landmarker.task` 존재(없으면 `MODEL_LOAD_FAILED`) | 0 |
| 2 | 손 x축 미러링이 A 규약과 일치(어긋나면 화면 반대편 터치) | 1 |
| 3 | reach 캘리브레이션이 실제 손으로 완료 | 1 |
| 4 | 제스처 오검출률(게이트 기본값 실사용 가능) | 2 |
| 5 | 상태 게이트키핑이 제스처에도 정상 적용 | 2 |
| 6 | 모드 전환 중 드래그 안전 취소, 음성 안전명령 상시 | 3 |
| 7 | FACE+음성 회귀(기존 경로 무영향) | 매 Phase |
| 8 | HYBRID fps/발열 실측 | 4 |

---

## 테스트 전략

- **GestureClassifier**: 순수 로직 → D의 `CommandGate` 전수 테스트처럼 유닛테스트. 손 랜드마크 시퀀스를 Mock으로 주입, 각 제스처/게이트(유지프레임·쿨다운) 검증. 기존 `testDebugUnitTest`에 합류.
- **HandLandmarkMapper**: reach 정규화/클램핑 경계값 유닛테스트.
- **배선(merge)**: `MockVoiceCommandSource` 패턴을 참고해 gesture Flow mock → merge 후 순서 보존 확인.
- 계약 타입에 필드 추가 시 **뒤에 기본값으로만**(INTEGRATION §2.3) → 기존 32개 유닛테스트 무변경 유지.

---

## 파일 요약 (신규 7 · 수정 4 · 이동 1)

**신규**: `HandTracker` · `HandLandmarkMapper` · `GestureClassifier` · `HandGesture` ·
`HandPointerSource` · `GestureCommandSource`(+`MergedCommandSource`) · `InputModeController` · (P4)`FrameHub`
**수정**: `RealConductorDependencies` · `ControllerPipeline` · `ControllerActivity` · `CalibrationStore`
**이동(로직 불변)**: `toUprightBitmap`/`isLowLight` → `CameraFrameUtils`
**무변경(핵심 자산)**: D 포트 4개 · `Orchestrator` · `CommandGate`/`StateHolder`/`TransitionTable` ·
`CalibrationController` · C `InputExecutionEngine` · 오버레이 · (P4 전까지)`FaceTracker`
