# 핸드오프 문서 — C 모듈 (Input Execution Engine)

> 핸즈프리 스마트폰 컨트롤러 / 담당: 터치 · 뒤로가기 · 드래그 · 스크롤 · 페이지 전환 실행 (FR-002 / FR-003 / FR-004)
> 브랜치: `partC` · 상태: **구현 완료, Mock 버튼 기반 실기기 설치/검증 진행, 실제 A/D 통합 대기**

이 문서는 **C를 A/D와 통합할 사람**과 **C 코드를 이어받을 사람**을 위한 안내서다.

---

## 1. C가 하는 일 (한눈에)

C는 시스템의 "손"이다. 음성 인식이나 상태 판단을 하지 않고, 다음만 한다:

1. **포인터 좌표 보관** — A가 주는 최신 `PointerFrame`과 마지막 유효 좌표를 저장
2. **실행 명령 처리** — D가 상태 검증을 끝낸 `ExecutionCommand`만 받아 실행
3. **OS 입력 이벤트 발생** — Android Accessibility API로 탭/뒤로가기/스와이프/드래그 실행
4. **드래그 내부 실행 상태 관리** — `DRAG_START` 이후 좌표 업데이트를 따라 드래그를 이어가고 `DRAG_END`/`DRAG_CANCEL`로 종료
5. **실행 결과 보고** — 성공/실패와 실패 사유를 `ExecutionResult`로 D에 반환

> C는 "지금 ACTIVE인지", "잠금 상태인지", "이 명령이 허용되는지"를 판단하지 않는다. 그 판단은 D가 끝낸 뒤 C에 명령을 내려준다.

---

## 2. 빌드 · 실행 · 테스트

- **요구**: Android Studio, Android SDK, USB 디버깅 가능한 Android 기기
- **열기**: Android Studio에서 프로젝트 루트 열기 → Gradle Sync
- **빌드**:

```powershell
java -jar .\gradle\wrapper\gradle-wrapper.jar :app:assembleDebug
```

- **실기기 설치**:

```powershell
java -jar .\gradle\wrapper\gradle-wrapper.jar :app:installDebug
```

- **기기 연결 확인**:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

### 접근성 서비스 활성화

C의 실제 OS 입력은 `GestureAccessibilityService`가 연결되어야 동작한다. 설치 후 기기에서 다음을 켠다:

```text
설정 > 접근성 > 설치된 앱/다운로드한 앱 > Hands_free_controller
```

앱의 Mock 화면에도 `Accessibility Settings`, `Refresh Status` 버튼이 있다.

### Mock 테스트 화면

현재 `MainActivity`는 C 단독 검증용 Mock 패널이다.

- **Mock A: PointerFrame**
  - `Center`, `Top`, `Bottom`, `Left`, `Right`: 가짜 좌표 주입
  - `Face Lost`: `faceDetected=false` 주입
- **Mock D: ExecutionCommand**
  - `TOUCH`, `BACK`
  - `DOWN`, `UP`, `SMALL DOWN`, `LARGE DOWN`
  - `NEXT`, `PREV`
- **Mock Drag**
  - `Drag Start` → 현재 좌표에서 누름 시작
  - `Move Down` / `Move Up` → A가 새 좌표를 보내는 상황 흉내
  - `Drag End` → 손 떼기
  - `Drag Cancel` → C 내부 드래그 상태 정리

> Mock 버튼 자체도 같은 앱 화면 위의 실제 터치이고, C가 만든 입력도 같은 화면 위에 들어간다. 그래서 드래그 좌표가 버튼 영역과 겹치면 버튼이 눌린 것처럼 보일 수 있다. 이건 접근성 입력이 실제로 앱 화면에 전달되고 있다는 뜻이기도 하다.

> `local.properties`는 machine-specific(SDK 경로)이라 `.gitignore` 처리됨. 각자 로컬에서 Android Studio가 생성하거나 직접 `sdk.dir=...`을 둔다.

---

## 3. 인터페이스 계약 (A/D ↔ C)

패키지 `com.example.hands_free_controller.input`에 C가 사용하는 데이터 타입이 있다.

### C가 받는 것

| 제공자 | API | 데이터 | 설명 |
| --- | --- | --- | --- |
| **A** | `InputExecutionEngine.updatePointerFrame(frame)` | `PointerFrame(timestamp, x, y, faceDetected, confidence)` | 매 프레임 포인터 좌표. `x`, `y`는 정규화 0~1 |
| **D** | `InputExecutionEngine.execute(command, onResult)` | `ExecutionCommand(commandId, timestamp)` | D가 상태 검증을 끝낸 실행 명령 |

### C가 주는 것

| 대상 | 데이터 | 설명 |
| --- | --- | --- |
| **D** | `ExecutionResult(commandId, success, executedAt, timestamp, errorReason)` | 실행 결과. D는 오버레이 피드백/로그에 사용 |

### 현재 C가 처리하는 `CommandId`

```
TOUCH, BACK,
DRAG_START, DRAG_END, DRAG_CANCEL,
SCROLL_DOWN, SCROLL_UP, SCROLL_DOWN_SMALL, SCROLL_UP_SMALL, SCROLL_DOWN_LARGE, SCROLL_UP_LARGE,
NEXT, PREV
```

제어 명령인 `STOP`, `RESUME`, `LOCK`, `UNLOCK`은 C가 처리하지 않는다. 이들은 OS 입력 이벤트가 아니라 D의 상태 전이 명령이다.

### 명령별 동작

| commandId | C 동작 |
| --- | --- |
| `TOUCH` | 현재 포인터 좌표에서 탭 |
| `BACK` | Android 시스템 뒤로가기 |
| `DRAG_START` | 현재 좌표에서 드래그 시작, C 내부 `isDragging=true` |
| `DRAG_END` | 현재 좌표에서 드래그 종료 |
| `DRAG_CANCEL` | C 내부 드래그 상태 및 접근성 stroke 정리 |
| `SCROLL_DOWN` / `SCROLL_UP` | 화면 높이 약 50% 스와이프 |
| `SCROLL_DOWN_SMALL` / `SCROLL_UP_SMALL` | 화면 높이 약 20% 스와이프 |
| `SCROLL_DOWN_LARGE` / `SCROLL_UP_LARGE` | 화면 높이 약 80% 스와이프 |
| `NEXT` / `PREV` | 발표/페이지 전환용 좌우 스와이프. 시스템 뒤로가기/앞으로가기가 아님 |

### 실패 코드

| errorReason | 의미 |
| --- | --- |
| `NO_POINTER` | 유효한 포인터 좌표를 받은 적 없음 |
| `STALE_POINTER` | 최신 포인터가 `faceDetected=false`라 마지막 유효 좌표로 실행 |
| `ACCESSIBILITY_SERVICE_NOT_CONNECTED` | 접근성 서비스가 꺼져 있거나 아직 연결되지 않음 |
| `OS_INJECTION_FAILED` | Android 접근성 제스처 실행 실패 |
| `INVALID_SEQUENCE` | `DRAG_START` 없이 `DRAG_END`/`DRAG_CANCEL`이 들어온 경우 |

---

## 4. 통합 방법 (★ A/D 담당 필독)

현재 C는 직접 클래스 호출 방식으로 구현되어 있다.

```kotlin
val engine = InputExecutionEngine(context.applicationContext)
```

### 4.1 A 좌표 연결

A는 포인터 좌표가 갱신될 때마다 C에 전달한다.

```kotlin
engine.updatePointerFrame(
    PointerFrame(
        timestamp = System.currentTimeMillis(),
        x = normalizedX,
        y = normalizedY,
        faceDetected = faceDetected,
        confidence = confidence
    )
)
```

- `x`, `y`는 0~1 범위로 합의한다.
- C는 화면 밖 좌표를 `0f..1f`로 보정한 뒤 실제 픽셀 좌표로 변환한다.
- `faceDetected=false`가 오면 최신 좌표로는 쓰지 않고, 마지막 유효 좌표를 fallback으로 쓴다.

### 4.2 D 명령 연결

D는 상태 게이트를 통과한 명령만 C에 보낸다.

```kotlin
engine.execute(ExecutionCommand(CommandId.TOUCH)) { result ->
    // D에서 오버레이 클릭 피드백, 로그, 실패 안내 등에 사용
}
```

D가 C로 보내면 안 되는 것:

```text
STOP, RESUME, LOCK, UNLOCK
```

이 네 가지는 D 내부 상태 전이로만 처리한다.

### 4.3 드래그 연결

드래그는 D가 시작/종료 명령만 내려주고, 중간 이동은 A의 좌표 스트림으로 이어진다.

```text
A → C: PointerFrame 계속 업데이트
D → C: DRAG_START
C: isDragging = true
A → C: PointerFrame 계속 업데이트
C: updatePointerFrame() 안에서 continueDrag()
D → C: DRAG_END 또는 DRAG_CANCEL
```

즉, D는 드래그 중 매 프레임 C에게 "이동해도 되는지" 묻지 않는다. D는 타임아웃/취소 정책을 담당하고, 필요하면 `DRAG_CANCEL`을 내려준다.

---

## 5. A/D와 최종 합의 필요 사항

| 상대 | 항목 |
| --- | --- |
| **A** | 좌표계가 정규화 0~1인지 · 화면 회전/상태바/내비게이션바 포함 기준 · `faceDetected=false`와 `confidence` 임계값 정책 |
| **D** | C로 내려줄 `CommandId` 목록 · `STOP/LOCK` 등 제어명령을 C로 보내지 않는 규칙 · 드래그 자동취소 타임아웃 기본값(예: 30초) · `ExecutionResult.errorReason` 처리 방식 |
| 공통 | timestamp는 epoch millis 기준인지 · 공유 데이터 타입을 별도 `:contracts` 모듈로 분리할지 · 패키지명을 팀 통합 패키지(`com.mobileconductor` 등)로 맞출지 |

> 현재 C 브랜치 패키지는 `com.example.hands_free_controller`다. D 브랜치가 `com.mobileconductor` 기반이라면 통합 시 패키지/모델 위치를 맞추는 작업이 필요하다.

---

## 6. 코드 맵

```
app/src/main/java/com/example/hands_free_controller/
  MainActivity.kt                         C 단독 검증용 Mock UI
  input/
    CommandId                             C가 처리하는 실행 명령 enum
    ExecutionCommand.kt                   D → C 명령
    ExecutionResult.kt                    C → D 결과
    PointerFrame.kt                       A → C 좌표
    InputExecutionEngine.kt               C 핵심 엔진. 좌표 보관, 명령 분기, 드래그 상태, 결과 반환
  service/
    GestureAccessibilityService.kt        Android Accessibility API 래퍼. tap/back/swipe/drag 실행
  ui/theme/                               Android Studio 기본 Compose 테마

app/src/main/res/xml/
  accessibility_service_config.xml        접근성 서비스 설정

app/src/main/AndroidManifest.xml          접근성 서비스 등록
```

핵심 흐름:

```text
A(PointerFrame) ─┐
                 ├─> InputExecutionEngine ─> GestureAccessibilityService ─> Android OS 입력
D(ExecutionCommand) ┘              │
                                   └─> ExecutionResult ─> D/Overlay 피드백
```

---

## 7. 알아둘 점 / 한계

- **접근성 서비스 필수**: 서비스가 꺼져 있으면 모든 OS 입력 실행은 `ACCESSIBILITY_SERVICE_NOT_CONNECTED`로 실패한다.
- **Mock UI는 개발용**: A/D/B가 붙기 전 C만 검증하기 위한 화면이다. 최종 앱에서는 제거하거나 debug 빌드에서만 노출한다.
- **드래그 구현 차이**: Android 8.0 이상에서는 `continueStroke()` 기반 연속 드래그를 사용한다. 8.0 미만에서는 `DRAG_END` 시점에 시작점~끝점 단발 드래그로 폴백한다.
- **`NEXT`/`PREV` 의미**: 시스템 앞으로/뒤로가 아니라 발표/문서/갤러리 등에서의 좌우 페이지 전환 스와이프다.
- **테스트 환경**: 실제 설치는 SM-G930K(Android 8.0.0)에서 확인. 접근성 입력 특성상 앱 화면 위에서 테스트하면 Mock 버튼이 함께 눌리는 것처럼 보일 수 있다.
- **자동 테스트 없음**: Android Accessibility 제스처는 실기기 육안 검증 비중이 크다. 추후 좌표 변환/명령 분기 로직은 인터페이스 분리 후 유닛 테스트를 추가하는 것이 좋다.

---

## 8. 참고 문서

- `docs/전체 기능명세서.md` — 전체 기능 명세
- `docs/기능명세서 for C.md` — C 파트 명세(담당 범위)
- `docs/기획안(핸즈프리 스마트폰 컨트롤러).md` — 프로젝트 기획안
