# Part D — 상태 관리 및 오케스트레이터

## 현재 역할

D는 `ControllerState`를 소유하고, B가 보낸 `CommandId`가 현재 상태에서 유효한지 판단한 뒤 C에 실행 명령을 전달한다.

## 현재 상태

```kotlin
CALIBRATING, ACTIVE, LOCKED
```

## 현재 CommandId

```kotlin
TOUCH, BACK,
SCROLL_DOWN, SCROLL_UP,
NEXT, PREV,
LOCK, UNLOCK
```

## 유효성 표

| commandId | CALIBRATING | ACTIVE | LOCKED |
| --- | --- | --- | --- |
| TOUCH | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| BACK | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| SCROLL_DOWN | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| SCROLL_UP | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| NEXT | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| PREV | Reject | Accept(exec) | Reject(NEED_UNLOCK) |
| LOCK | Reject | Accept(LOCKED, no exec) | Reject(NEED_UNLOCK) |
| UNLOCK | Reject | Reject | Accept(ACTIVE, no exec) |

## 제거된 범위

- `PAUSED`, `DRAGGING`
- `STOP`, `RESUME`, `DRAG_START`, `DRAG_END`, `DRAG_CANCEL`
- `DragWatchdog`
- `MockConductorDependencies`, `MockCalibrationConsumer`

테스트용 `MockVoiceCommandSource`, `MockPointerSource`, `MockExecutionSink`는 유지한다.
