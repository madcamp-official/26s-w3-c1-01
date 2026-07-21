# Part D Plan — 현재 유지보수 기준

## 현재 목표

상태 머신은 MVP 명령 정책에 맞춰 작게 유지한다. 드래그와 일시정지는 현재 제품 범위에서 제외한다.

## 상태/명령 조합

- 상태: `CALIBRATING`, `ACTIVE`, `LOCKED`
- D `CommandId`: `TOUCH`, `BACK`, `SCROLL_DOWN`, `SCROLL_UP`, `NEXT`, `PREV`, `LOCK`, `UNLOCK`
- 전체 게이트 조합: 3 상태 × 8 명령 = 24

## 구현 원칙

- `CommandDictionary`와 `CommandId`는 동기화되어야 한다. 단, `EXIT`는 앱 종료 특수 명령이라 D enum 밖에서 처리한다.
- `LOCK`/`UNLOCK`은 상태 전이 전용이라 `ExecutionCommand`로 C에 전달하지 않는다.
- 실제 OS 이벤트가 필요한 명령만 C로 전달한다.
- 캘리브레이션 완료는 음성 명령이 아니라 `Orchestrator.onCalibrationComplete()`로 처리한다.

## 제거된 설계

- 드래그 상태와 자동 취소 타이머
- 일시정지/재개 상태
- 스크롤 강도별 명령
- 통합 전용 mock dependency bundle
