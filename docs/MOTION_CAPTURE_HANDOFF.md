# 핸드오프 문서 — 모션 캡쳐(손) 입력 모달리티

> 핸즈프리 스마트폰 컨트롤러 / 손 추적으로 포인터 이동 + 손 제스처로 명령
> 브랜치: `feature/motion-capture-hand` (base: `integration`) · 커밋: `fc145a9`
> 상태: **코드 작성 완료(Phase 0~3). 빌드/실기기 전혀 미검증.**

이 문서는 **이 브랜치를 빌드해서 실기기로 검증할 사람**과 **이어서 Phase 4를 진행할 사람**을 위한 안내서다.

---

## 1. 이게 하는 일 (한눈에)

기존 컨트롤러는 **포인터 = 얼굴/시선**, **명령 = 음성** 두 채널이었다. 여기에 전면 카메라로
손을 추적하는 **세 번째 입력 모달리티**를 추가했다.

1. **손 포인터** — 검지 끝(MediaPipe HandLandmarker #8)을 화면 좌표로 매핑. 얼굴과 같은
   9점 캘리브레이션 플로우를 그대로 통과한다(아래 §3 참고).
2. **손 제스처 → 명령** — 핀치(TOUCH) · 주먹(DRAG_START) · 손펴기(DRAG_END) ·
   엄지척(RESUME) · 스와이프 4방향(SCROLL_UP/DOWN, PREV/NEXT). 제스처는 음성과
   **같은 명령 스트림**으로 합쳐져 기존 상태 머신이 그대로 게이트키핑한다.
3. **입력 모드 전환** — 설정 화면 버튼으로 FACE ↔ HAND. 카메라는 한 번에 하나만
   쓴다(동시 아님). 모드별로 캘리브레이션이 따로 저장된다.

**핵심 설계 원칙: D의 포트 4개(`PointerSource`/`VoiceCommandSource`/`ExecutionSink`/
`CalibrationConsumer`)를 한 줄도 안 건드렸다.** 오케스트레이터·상태머신(`CommandGate`)·
C의 실행부·오버레이는 전부 무변경이다. 손 추적은 이 포트에 꽂히는 어댑터로만 구현했다.

> 왜 이게 가능했는지, 설계 근거 전체는 [MOTION_CAPTURE_SPEC.md](MOTION_CAPTURE_SPEC.md)와
> [MOTION_CAPTURE_PLAN.md](MOTION_CAPTURE_PLAN.md)에 있다. 이 문서는 "지금 뭘 확인해야
> 하는가"에 집중한다.

---

## 2. 빌드 · 실행 · 테스트 — ⚠️ 여기부터가 실제 작업

**이 작업 환경엔 Android SDK/Gradle이 없어서 단 한 번도 컴파일하지 못했다.**
INTEGRATION.md §5와 같은 성격의 경고다: **코드는 다 있지만 동작 확인은 전무하다.**

### 2.1 가장 먼저 할 일 — 컴파일

1. Android Studio에서 `feature/motion-capture-hand` 체크아웃 → Gradle Sync.
2. **가장 위험한 지점: `HandTracker.kt`의 MediaPipe `HandLandmarker` API.**
   `FaceLandmarker`(기존 코드, 검증됨)의 패턴을 보고 손 버전도 같은 형태일 거라
   **추측해서** 작성했다 — `HandLandmarker.HandLandmarkerOptions.builder()`,
   `setNumHands()`, `setMinHandDetectionConfidence()`, `setMinTrackingConfidence()`,
   `result.landmarks()`, 랜드마크의 `.x()`/`.y()`/`.z()` 등. `tasks-vision:0.10.18`
   실제 문서/소스로 대조 검증한 적이 없다. **메서드명이 하나라도 다르면 컴파일이
   그 자리에서 깨진다.** 깨지면 여기부터 고친다.
3. `gradlew testDebugUnitTest` — 기존 32개(D)+B 사전 테스트에 더해 신규
   `GestureClassifierTest`(합성 랜드마크로 손모양·스와이프·게이트 전수 검증,
   MediaPipe 의존 없는 순수 로직)가 통과해야 한다.
4. `app/src/main/assets/hand_landmarker.task`(7.5MB)가 있는지 확인. 이번에
   `README.md`/`.gitignore`를 갱신해 저장소에 커밋했다 — 없으면 `MODEL_LOAD_FAILED`.

### 2.2 실기기 — 확인 순서(위험한 것부터)

| # | 확인 항목 | 안 맞으면 |
| --- | --- | --- |
| 1 | **손 좌우 미러링.** 손을 오른쪽으로 움직이면 커서도 오른쪽으로 가는지 | `HandTracker.MIRROR_X` 상수 하나만 뒤집는다(컴파일로 안 잡힘) |
| 2 | **회전 보정.** 기기를 눕혔을 때 `toScreenSpace()`의 90/270 매핑이 맞는지 | 얼굴은 각도 기반, 손은 좌표 기반이라 식이 다르다 — 새로 검증 필요 |
| 3 | **HAND 모드 캘리브레이션**이 실제 손으로 9점 다 채워지는지 | `CalibrationController`는 얼굴로만 검증됐다. 손 도달범위가 카메라 화각 안에서 자연스럽게 나오는지 확인 |
| 4 | **제스처 오검출률.** 핀치/주먹/손펴기/엄지척 구분이 실제로 되는지 | `GestureClassifier` 하단 상수(`PINCH_RATIO`, `THUMB_EXT_RATIO`, `STILL_THRESHOLD`, `SWIPE_MIN_DISTANCE`, `MIN_HOLD_FRAMES`, 쿨다운) 튜닝 |
| 5 | **모드 전환 시 카메라 재바인딩**이 매끄러운지(끊김/크래시 없음) | `InputModeController.activate()`의 stop→start 순서 확인 |
| 6 | **전환 중 드래그 안전 취소**가 실제로 되는지 | `ControllerPipeline.switchMode()` |
| 7 | **fps/발열** — HAND 단독 fps가 얼굴 단독(갤럭시 S7 13~16fps)과 비슷한지 | 손 모델이 얼굴보다 크다(7.5MB vs 3.7MB) |

---

## 3. 아키텍처 요지 — "스위처블 소스"

D(오케스트레이터)는 `PointerSource` 하나만 붙잡고 평생 쓴다. 런타임에 FACE↔HAND를
바꾸려면 **그 안정 객체는 그대로 두고 내부가 가리키는 트래커만 바꿔야** 한다.

```
InputModeController
  ├─ _active: MutableStateFlow<PointerTracker?>   ← 현재 트래커(FaceTracker | HandTracker)
  ├─ pointerSource: PointerSource                 ← _active를 flatMapLatest로 따라감
  └─ gestureLandmarks: Flow<HandLandmarks>         ← 위와 동일

activate(HAND) 호출 시:
  1. 이전 트래커 stop()
  2. HandTracker 생성 + start()
  3. _active.value = 새 트래커  → pointerSource가 자동으로 새 트래커를 구독
```

오케스트레이터·`RealConductorDependencies`·오버레이는 **재생성되지 않는다.** 이게
"모드 전환에도 D 포트가 안 바뀐다"의 실제 구현 방식이다.

**캘리브레이션 재사용(가장 핵심적인 트릭)**: 손의 화면 위치를 `RawFaceOrientation`의
`yaw`/`pitch` 슬롯에 그대로 싣는다. 그래서 `CalibrationController`(D 소유, 9점 수집)가
한 줄도 안 바뀌고 손 도달범위도 수집한다 — 그 컨트롤러는 min/max만 보지 얼굴/손을
구분하지 않는다. `CalibrationProfile`의 필드명이 여전히 "face" 의미로 남아 있는 건
알려진 찜찜함이다(§5 참고).

**제스처 → 명령 합류**: `GestureCommandSource`가 제스처를 `VoiceCommandEvent`로
감싸고, `MergedCommandSource`가 이걸 음성 스트림과 `merge`한다. `CommandGate`는
명령의 출처를 모른다 — `CommandId`만 본다.

---

## 4. 코드 맵

```
tracking/
  PointerTracker.kt        FACE/HAND 공통 계약(start/stop/updateProfile/에러/좌표 스트림)
  FaceTracker.kt           (기존, 수정) PointerTracker 구현 + 카메라 유틸 추출
  HandTracker.kt           (신규) HandLandmarker + CameraX. FaceTracker와 대칭 구조
  HandLandmarkMapper.kt    (신규) 손 도달범위 정규화 + Smoother 재사용(pitch결합·시선보조 없음)
  HandLandmarks.kt         (신규) 순수 데이터(21점 + 화면좌표 tip) — MediaPipe 타입 격리
  HandGesture.kt           (신규) 제스처 enum 8종
  GestureClassifier.kt     (신규) 손모양/스와이프 판정. 순수 로직 → 유닛테스트 대상
  CameraFrameUtils.kt      (신규) toUprightBitmap/isLowLight, FaceTracker에서 추출(로직 불변)

integration/
  InputModeController.kt   (신규) 트래커 수명 + 스위처블 소스. InputMode enum 여기 있음
  HandPointerSource.kt     (신규) HandTracker → D PointerSource (FacePointerSource와 대칭)
  GestureCommandSource.kt  (신규) 제스처 → CommandId → VoiceCommandEvent, 쿨다운 게이트
  MergedCommandSource.kt   (신규) 음성+제스처 merge (포트 무변경의 핵심)
  RealConductorDependencies.kt  (수정) 스위처블 소스 + 활성 트래커 프로바이더로 배선
  TrackerCalibrationConsumer.kt (수정) 활성 트래커 프로바이더 기반, 모드별 저장
  CalibrationStore.kt      (수정) 모드별 prefs 파일 분리 + 마지막 모드 영속화
  ControllerPipeline.kt    (수정) switchMode()/requestMode()/recalibrate() 추가
  ControllerActivity.kt    (수정) 모드 토글 버튼

test/…/tracking/
  GestureClassifierTest.kt (신규) 합성 랜드마크로 손모양·스와이프·게이트·리셋 전수 검증
```

---

## 5. 알려진 문제 / 다음 사람과 합의 필요 (MOTION_CAPTURE_SPEC.md §9 참고)

| 항목 | 내용 |
| --- | --- |
| **MediaPipe HandLandmarker API 추측** | §2.1의 위험. 최우선 확인 대상 |
| `CalibrationProfile` 필드명 | HAND 모드에서도 `faceRangeYawMin` 등 "face" 이름을 그대로 씀(재사용을 위해 의도적). 리네임 여부는 팀 결정 |
| 손 잠금/정지 제스처 없음 | STOP/LOCK/UNLOCK/BACK은 제스처에서 의도적으로 제외 — 오작동 위험 때문에 음성·버튼 전담으로 남김. 팀이 원하면 다시 넣을 수 있음 |
| 음성으로 모드 전환 불가 | 지금은 설정 화면 버튼만. `CommandGate` 밖 경로가 필요해 범위 밖으로 미룸 |
| HYBRID(얼굴+손 동시) 미구현 | 카메라 1대 제약. FrameHub 도입 + **A `FaceTracker`의 카메라 소유 분리(리팩터링)** 필요 → A파트 합의 없이 진행 불가. Phase 4 |
| 제스처 임계값 미세팅 | `GestureClassifier` companion object의 상수들, 전부 실기기 튜닝 대상 |
| A파트 코드 변경 리뷰 안 됨 | `FaceTracker.kt`에서 private 함수 2개를 `CameraFrameUtils.kt`로 추출(로직 불변, 위치만 이동). A파트 확인 필요 |

---

## 6. 다음 사람이 할 일 (체크리스트)

- [ ] Android Studio Gradle Sync → 컴파일 에러 확인·수정(특히 HandLandmarker API)
- [ ] `gradlew testDebugUnitTest` 통과 확인
- [ ] 실기기에 설치, FACE 모드가 기존과 동일하게 동작하는지 회귀 확인(가장 중요 — 이 브랜치가 기존 기능을 깨면 안 됨)
- [ ] HAND 모드 진입 → 캘리브레이션 → 손으로 커서 이동 확인
- [ ] 미러링/회전 부호 확인, 필요시 상수 수정
- [ ] 제스처 8종 각각 시연, 오검출률 체감 확인 → 임계값 튜닝
- [ ] 모드 전환 버튼으로 FACE↔HAND 왕복, 드래그 중 전환 안전성 확인
- [ ] 위 항목 정리해서 PR 생성(현재 브랜치만 푸시된 상태, PR 없음)

---

## 7. 참고 문서

- [MOTION_CAPTURE_SPEC.md](MOTION_CAPTURE_SPEC.md) — 기능 명세, 미결정 사항(§9) 전체
- [MOTION_CAPTURE_PLAN.md](MOTION_CAPTURE_PLAN.md) — Phase별 구현 계획과 설계 근거(축 1/축 2)
- [INTEGRATION.md](INTEGRATION.md) — A/B/C/D 통합 기록. 같은 성격의 "미검증 상태" 경고 문서
- [FUNCTIONAL_SPEC.md](FUNCTIONAL_SPEC.md) — 전체 기능 명세(얼굴/음성 경로 원본)
