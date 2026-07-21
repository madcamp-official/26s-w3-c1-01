# A/B/C/D 통합 기록

> 현재 정책 기준으로 갱신됨.

## 1. 앱 구조

통합 앱의 `namespace`와 `applicationId`는 `com.madcamp.handsfree`다. 소스 패키지는 파트별 원본을 유지한다.

| 패키지 | 역할 |
| --- | --- |
| `com.madcamp.handsfree.tracking` | 얼굴 추적 및 포인터 프레임 생성 |
| `com.madcamp.handsfree.voice` | SpeechRecognizer 래퍼와 명령어 사전 |
| `com.madcamp.handsfree.integration` | A/B/C/D 포트 어댑터와 Activity |
| `com.mobileconductor.orchestrator` | 상태 머신, 게이트, 캘리브레이션 조립 |
| `com.example.hands_free_controller` | 접근성 기반 OS 입력 실행 |

## 2. 현재 명령/상태 계약

현재 D의 `CommandId`는 8종이다.

```kotlin
TOUCH, BACK,
SCROLL_DOWN, SCROLL_UP,
NEXT, PREV,
LOCK, UNLOCK
```

`CommandDictionary`에는 특수 문자열 명령 `EXIT`가 하나 더 있다. `EXIT`는 D의 enum에 넣지 않고 `VoiceCommandSourceAdapter`가 직접 처리해 서비스를 종료한다.

현재 `ControllerState`는 3종이다.

```kotlin
CALIBRATING, ACTIVE, LOCKED
```

유효한 상태 전이는 다음뿐이다.

| 상태 | 유효 명령 |
| --- | --- |
| CALIBRATING | 없음 |
| ACTIVE | TOUCH, BACK, SCROLL_DOWN, SCROLL_UP, NEXT, PREV, LOCK |
| LOCKED | UNLOCK |

## 3. 제거된 정책

다음 기능은 현재 MVP 정책에서 제거했다.

- 드래그: `DRAG_START`, `DRAG_END`, `DRAG_CANCEL`, `DRAGGING`, `DragWatchdog`
- 일시정지/재개: `STOP`, `RESUME`, `PAUSED`
- 스크롤 강도: `SCROLL_DOWN_SMALL`, `SCROLL_UP_SMALL`, `SCROLL_DOWN_LARGE`, `SCROLL_UP_LARGE`
- 통합 전 Mock 의존성 묶음: `MockConductorDependencies`, `MockCalibrationConsumer`

테스트에서 직접 쓰는 `MockVoiceCommandSource`, `MockPointerSource`, `MockExecutionSink`는 유지한다.

## 4. 통합 어댑터

| 파일 | 하는 일 |
| --- | --- |
| `FacePointerSource` | FaceTracker Flow를 D의 `PointerSource`로 제공 |
| `VoiceCommandSourceAdapter` | B의 콜백 이벤트를 D의 Flow로 변환. `EXIT`는 직접 종료 처리 |
| `InputExecutionSink` | D의 실행 명령을 C의 접근성 입력 엔진으로 전달 |
| `TrackerCalibrationConsumer` | 완성된 보정 프로파일을 FaceTracker와 로컬 저장소에 반영 |
| `RealConductorDependencies` | 실제 A/B/C 포트 구현 묶음 |
| `ControllerActivity` | 권한, 오버레이, 접근성 설정, 캘리브레이션 시작 UI |

## 5. 검증 명령

이 PC에서는 `gradlew.bat`의 빈 classpath 문제가 있어 wrapper jar를 직접 실행한다.

```powershell
java -jar .\gradle\wrapper\gradle-wrapper.jar assembleDebug
java -jar .\gradle\wrapper\gradle-wrapper.jar testDebugUnitTest
```
