# 기능명세서 for C

# 기능 명세서 [C] — 터치/드래그/스크롤 실행 엔진 (Input Execution Engine)

- 담당 FR: FR-002 (기본 터치), FR-003 (드래그), FR-004 (스크롤)
- 역할 한 줄 요약: A가 주는 좌표 + D가 승인한 명령만 받아서 실제 OS 입력 이벤트(탭/드래그/스와이프)로 바꾸는 "손" 역할. 지금 상태가 ACTIVE인지 PAUSED인지는 몰라도 된다 — D가 이미 걸러서 명령을 내려주기 때문이다.

---

## 1. 역할 개요 (Scope)

**한다**

- `ExecutionCommand`(D가 유효성 검증을 마친 명령)를 받아 OS 접근성 API 등을 통해 실제 탭/드래그/스와이프 이벤트 생성
- 드래그 중에는 D의 추가 명령 없이도 A의 최신 좌표를 계속 받아 드래그 이동을 자체적으로 이어감 (매 프레임 D에게 물어보지 않음 — 성능/구조상 자체 관리)
- 실행 성공/실패를 `ExecutionResult`로 D에 보고 (오버레이 피드백용)

**하지 않는다 (다른 역할 책임)**

- 지금 명령이 유효한 상태인지 판단 → D가 이미 검증해서 넘겨준 것만 받음
- 음성 인식/명령어 동의어 처리 → B 책임
- 포인터 좌표 계산 → A 책임

> C는 "생각 없는 손"이다. D가 "지금 터치해도 돼"라고 내려주면 그 순간 A의 최신 좌표에서 탭을 실행할 뿐, 스스로 상태를 판단하지 않는다. 이렇게 해야 C팀이 D의 상태 머신 완성 여부와 무관하게, 가짜 `ExecutionCommand`만으로 실제 OS 입력 이벤트 생성 로직을 먼저 완성할 수 있다.
> 

---

## 2. 기능별 처리 로직

### 2.1 기본 터치 (FR-002)

1. `ExecutionCommand.commandId = TOUCH` 또는 `BACK` 수신
2. `TOUCH`: A로부터 받은 최신 `PointerFrame` 좌표에서 OS 탭 이벤트 생성
3. `BACK`: OS 뒤로가기 이벤트 생성 (좌표 불필요)
4. 실행 후 `ExecutionResult` 반환 (성공/실패, 실행 좌표 포함)

### 2.2 드래그 (FR-003)

1. `DRAG_START` 수신 → 내부 상태 `isDragging = true`로 전환, 현재 좌표를 시작점으로 기록, OS 터치-다운 이벤트 생성
2. `isDragging = true`인 동안, A로부터 새 `PointerFrame`이 도착할 때마다 OS 터치-이동 이벤트를 자체적으로 생성 (D의 추가 명령 불필요)
3. `DRAG_END` 수신 → 현재 좌표에서 OS 터치-업 이벤트 생성, `isDragging = false`
4. `DRAG_CANCEL` 수신 → 시작점으로 복귀시키되 터치-업 없이 드래그 취소 처리, `isDragging = false`

> `isDragging`은 C 내부에서만 쓰는 실행 상태이며, D가 관리하는 전역 `ControllerState.DRAGGING`과는 별개 개념이다(D는 UI 표시/명령 게이팅용, C는 실제 OS 이벤트 시퀀스 관리용).
> 

### 2.3 스크롤 (FR-004)

| commandId | 동작 | 스크롤 거리(화면 높이 대비) |
| --- | --- | --- |
| SCROLL_DOWN | 아래로(기본) | 약 50% |
| SCROLL_UP | 위로(기본) | 약 50% |
| SCROLL_DOWN_SMALL | 짧게 아래 | 약 20% |
| SCROLL_UP_SMALL | 짧게 위 | 약 20% |
| SCROLL_DOWN_LARGE | 길게 아래 | 약 80% |
| SCROLL_UP_LARGE | 길게 위 | 약 80% |
1. `ExecutionCommand.commandId`가 스크롤 계열일 때, A의 최신 좌표를 기준점으로 OS 스와이프 이벤트 생성
2. 위 표의 거리 값으로 스와이프 길이 결정

### 2.4 발표 페이지 전환 (NEXT/PREV, 시나리오 4 대응)

- `NEXT`/`PREV` 수신 시 좌우 스와이프 이벤트로 매핑 (스크롤과 동일한 처리 방식, 방향만 좌/우)

---

## 3. 인터페이스 계약

### 3.1 입력 ① `PointerFrame` (A → C) — [A 문서와 동일 스키마]

```json
{ "timestamp": 1721270000050, "x": 0.482, "y": 0.317, "faceDetected": true, "confidence": 0.88 }
```

C는 항상 가장 최근에 수신한 `PointerFrame`을 "현재 좌표"로 사용한다.

### 3.2 입력 ② `ExecutionCommand` (D → C, 이미 유효성 검증 완료)

```json
{
  "commandId": "DRAG_START",
  "timestamp": 1721270000000
}
```

- C는 이 명령이 "지금 실행해도 되는지" 재검증하지 않는다. D가 보낸 것은 곧 승인된 것으로 간주한다.

### 3.3 출력 `ExecutionResult` (C → D, 피드백용)

```json
{
  "commandId": "TOUCH",
  "success": true,
  "executedAt": { "x": 0.482, "y": 0.317 },
  "timestamp": 1721270000080,
  "errorReason": null
}
```

D는 이 결과로 오버레이 클릭 애니메이션 등 시각 피드백을 트리거한다.

---

## 4. 예외 처리

| 상황 | 처리 방안 |
| --- | --- |
| `TOUCH` 실행 시점에 유효한 `PointerFrame` 없음(예: faceDetected=false 지속) | 마지막 유효 좌표 사용, `ExecutionResult.success=false, errorReason="STALE_POINTER"` 보고 |
| 포인터 좌표가 화면 경계 밖 | 가장 가까운 유효 좌표로 보정 후 실행 |
| `DRAGGING` 도중 `PointerFrame` 수신 중단(얼굴 미검출) | 마지막 좌표에서 드래그 유지 (자동 취소하지 않음, D의 타임아웃 정책을 따름) |
| `DRAG_START` 없이 `DRAG_END`가 먼저 도착(비정상 순서) | 무시하고 `ExecutionResult.success=false, errorReason="INVALID_SEQUENCE"` 보고 |
| OS 접근성 API 호출 실패(권한 등) | `ExecutionResult.success=false, errorReason="OS_INJECTION_FAILED"` 보고 |

---

## 5. 병렬 개발 전략 (Mock 기준)

- C팀은 A, B, D의 완성을 기다리지 않는다.
- 더미 `PointerFrame` 스트림(예: 원을 그리며 움직이는 가짜 좌표)과 더미 `ExecutionCommand`(키보드 입력으로 임의 발생)를 자체 제작하여 OS 탭/드래그/스와이프 이벤트 생성 로직만 먼저 완성한다.
- OS 접근성 API 연동 자체가 난이도가 높으므로, 이 부분을 최우선 검증 대상으로 삼는다.

## 6. 완료 기준 (Definition of Done)

- [ ]  더미 `ExecutionCommand(TOUCH)` + 더미 좌표로 실제 OS 탭 이벤트 발생 확인
- [ ]  `DRAG_START` → 연속 좌표 변화 → `DRAG_END` 시퀀스가 하나의 자연스러운 드래그로 OS에 전달됨
- [ ]  `DRAG_CANCEL` 시 실제 이동/선택이 반영되지 않고 원상 복구됨
- [ ]  6종 스크롤 명령 각각 거리(20%/50%/80%)가 체감상 구분됨
- [ ]  모든 실행에 대해 `ExecutionResult`가 정확히 반환됨

## 7. 통합 시 A/D팀과 확인할 사항

- A와 좌표계(정규화 0~1) 단위 최종 합의
- D와 "얼마나 오래 `PointerFrame`이 끊기면 드래그를 자동 취소할지"(타임아웃 값, 예: 30초) 정책 합의 — 실제 타이머는 D가 갖고, 만료 시 `DRAG_CANCEL` 명령을 C로 내려주는 구조로 제안