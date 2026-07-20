# 기능 명세서 [D] — 상태 관리, 캘리브레이션, 오버레이 UI 엔진 (Orchestrator)

- 담당 FR: FR-005 (일시정지/재개), FR-006 (캘리브레이션), FR-008 (상태관리/오버레이) + 명령 유효성 검증(게이트키핑)
- 역할 한 줄 요약: 시스템의 "두뇌". `ControllerState`를 유일하게 소유하고, B의 명령이 지금 실행 가능한지 판단해서 C에게 승인된 명령만 내려주며, A/C의 데이터를 조합해 화면에 그린다. A, B, C는 이 모듈의 상태 머신이 어떻게 동작하는지 몰라도 되도록 설계되어 있다.

---

## 1. 역할 개요 (Scope)

**한다**

- `ControllerState` 전역 상태 소유 및 전이 관리 (CALIBRATING / ACTIVE / PAUSED / LOCKED / DRAGGING)
- B가 보낸 `VoiceCommandEvent`를 받아 "현재 상태에서 유효한 명령인지" 판정 → 유효하면 `ExecutionCommand`로 변환해 C에게 전달, 무효하면 폐기(+ 필요 시 안내)
- 캘리브레이션 플로우 진행: A의 `RawFaceOrientation`을 구독해 9개 기준점 데이터 수집 → `CalibrationProfile` 생성 → A에게 전달
- 오버레이 UI 렌더링: A의 `PointerFrame` + 자신의 `ControllerState`를 조합해 포인터/상태 인디케이터 표시
- C의 `ExecutionResult`를 받아 클릭 애니메이션 등 시각 피드백 트리거
- 드래그 장시간 방치 자동 취소, 잠금 해제 실패 대비 수동 버튼 등 안전장치 운용

**하지 않는다 (다른 역할 책임)**

- 카메라 프레임 처리, 좌표 계산 → A 책임
- 음성 인식, 명령어 동의어 처리 → B 책임 (D는 이미 정규화된 `commandId`만 받음)
- 실제 OS 탭/드래그/스와이프 이벤트 생성 → C 책임

---

## 2. 상태 정의 및 전이 (FR-008)

| 상태 | 설명 | 진입 조건 | 진출 조건 |
| --- | --- | --- | --- |
| CALIBRATING | 초기 보정 진행 중 | 앱 최초 실행, 재보정 요청 | 보정 완료 → ACTIVE |
| ACTIVE | 모든 유효 명령 처리 | 보정 완료, RESUME, UNLOCK | STOP→PAUSED, LOCK→LOCKED, DRAG_START→DRAGGING |
| PAUSED | RESUME만 유효 | STOP | RESUME→ACTIVE |
| LOCKED | UNLOCK만 유효 | LOCK | UNLOCK→ACTIVE |
| DRAGGING | DRAG_END/DRAG_CANCEL/STOP만 유효 | DRAG_START | DRAG_END/DRAG_CANCEL→ACTIVE |

```
CALIBRATING --(보정완료)--> ACTIVE
ACTIVE --STOP--> PAUSED --RESUME--> ACTIVE
ACTIVE --LOCK--> LOCKED --UNLOCK--> ACTIVE
ACTIVE --DRAG_START--> DRAGGING --DRAG_END/DRAG_CANCEL--> ACTIVE
DRAGGING --STOP(안전 우선)--> PAUSED (드래그 자동 취소 후 전이)
```

### 상태별 명령 유효성 표 (B → D 게이트키핑 로직의 핵심)

| commandId | ACTIVE | PAUSED | LOCKED | DRAGGING |
| --- | --- | --- | --- | --- |
| TOUCH, BACK | ✅ | ❌ | ❌ | ❌ |
| DRAG_START | ✅ | ❌ | ❌ | ❌ |
| DRAG_END, DRAG_CANCEL | ❌ | ❌ | ❌ | ✅ |
| SCROLL_* , NEXT, PREV | ✅ | ❌ | ❌ | ❌ |
| STOP | ✅ | ❌ | ❌ | ✅(안전 우선 처리) |
| RESUME | ❌ | ✅ | ❌ | ❌ |
| LOCK | ✅ | ✅ | ❌ | ❌ |
| UNLOCK | ❌ | ❌ | ✅ | ❌ |

> 이 표가 D의 핵심 로직이다. B가 어떤 `commandId`를 보내든, D는 이 표만 보고 통과/폐기를 결정한다.
> 

---

## 3. 캘리브레이션 플로우 (FR-006)

| 단계 | 내용 | 데이터 소스 |
| --- | --- | --- |
| 1 | 기기 거치 및 얼굴 정렬 안내 | UI 안내 |
| 2 | 화면 중앙 응시 | A의 `RawFaceOrientation` 구독 시작 |
| 3 | 상/하/좌/우 및 대각선 등 9개 기준점 순차 응시 | 기준점별로 A의 `RawFaceOrientation`을 일정 시간 평균하여 기록 |
| 4 | 얼굴/눈 움직임 범위 저장 | 9개 값 중 최소/최대로 `faceRangeYaw/Pitch` 산출 |
| 5 | 테스트 포인터로 민감도 조정 | 사용자가 `sensitivityLevel`/`smoothingLevel` 선택 → `CalibrationProfile`에 반영 |

**출력: `CalibrationProfile` (D → A)**

```json
{
  "profileId": "user_001",
  "referencePoints": [ { "yaw": -20.1, "pitch": 15.2 } /* ... 총 9개 */ ],
  "faceRangeYawMin": -25.0,
  "faceRangeYawMax": 25.0,
  "faceRangePitchMin": -18.0,
  "faceRangePitchMax": 18.0,
  "sensitivityLevel": "mid",
  "smoothingLevel": "mid",
  "createdAt": "2026-07-18T00:00:00Z",
  "updatedAt": "2026-07-18T00:00:00Z"
}
```

**예외 처리**

| 상황 | 처리 방안 |
| --- | --- |
| 특정 기준점에서 `faceDetected:false` 지속(5초 이상) | 해당 단계 재시도 안내, 3회 실패 시 처음부터 재시작 |
| 캘리브레이션 중도 이탈 | 임시 저장 없이 폐기 |
| 캘리브레이션 미완료 상태에서 앱 실행 | 자동으로 CALIBRATING 진입, ACTIVE 진입 차단 |
| 재보정 | 기존 프로파일로 계속 조작 가능 상태를 유지하다가, 재보정 완료 시점에 새 프로파일로 교체 |

---

## 4. 오버레이 UI 및 피드백 (FR-008)

| 상태 | 포인터 표시 | 상태 인디케이터 |
| --- | --- | --- |
| ACTIVE | 실시간 이동, 기본 색상 | 초록색 |
| PAUSED | 마지막 위치 고정, 반투명 | 노란색 + "일시정지" |
| LOCKED | 숨김/매우 반투명 | 회색 + "잠김" |
| DRAGGING | 이동 중, 테두리 강조 | 파란색 + "드래그 중" |
| CALIBRATING | 기준점 전용 UI | 진행률 |
- `ExecutionResult.success=true` 수신 시 해당 좌표에 짧은 클릭 애니메이션 표시
- LOCKED 상태에서 음성 인식이 반복 실패할 가능성에 대비해, 화면 내 수동 잠금 해제 버튼(물리 터치)을 항상 노출

---

## 5. 인터페이스 계약

### 5.1 입력 ① `PointerFrame` (A → D) / `RawFaceOrientation` (A → D, 캘리브레이션 중) — A 문서와 동일 스키마

### 5.2 입력 ② `VoiceCommandEvent` (B → D) — B 문서와 동일 스키마

### 5.3 입력 ③ `ExecutionResult` (C → D) — C 문서와 동일 스키마

### 5.4 출력 ① `CalibrationProfile` (D → A) — 위 3절 스키마

### 5.5 출력 ② `ExecutionCommand` (D → C)

```json
{ "commandId": "TOUCH", "timestamp": 1721270000000 }
```

### 5.6 내부 전용 `ControllerState`

```
CALIBRATING | ACTIVE | PAUSED | LOCKED | DRAGGING
```

> 다른 모듈에는 원칙적으로 노출하지 않는다(A, B, C는 상태를 몰라도 동작하도록 설계했으므로). 디버깅/로깅 목적으로만 외부에 읽기 전용으로 공개 가능.
> 

---

## 6. 예외 처리 (상태 충돌 등 D 고유 로직)

| 상황 | 처리 방안 |
| --- | --- |
| DRAGGING 중 STOP 발화 | 안전 우선: `DRAG_CANCEL`을 C에 먼저 내려보낸 뒤 PAUSED로 전이 |
| DRAGGING 상태가 일정 시간(예: 30초) 이상 지속 | D가 타이머로 감지 후 자동으로 `DRAG_CANCEL`을 C에 전달, 안내 메시지 표시 |
| LOCKED 상태에서 UNLOCK 외 명령 반복 수신 | 모두 폐기, "잠금 해제가 필요합니다" 안내 |
| A로부터 `CameraPermissionError` 수신 | 전체 기능 비활성화 안내, 권한 설정 화면 유도 |
| B로부터 `MicPermissionError` 수신 | 전체 기능 비활성화 안내, 권한 설정 화면 유도 |

---

## 7. 병렬 개발 전략 (Mock 기준)

- D팀은 A/B/C의 실제 구현을 기다리지 않는다.
- 더미 `PointerFrame`(고정 좌표 반복), 더미 `VoiceCommandEvent`(키보드 입력으로 임의 commandId 발생), 더미 `ExecutionResult`(항상 success:true)로 상태 머신과 게이트키핑 표, 캘리브레이션 플로우, 오버레이 렌더링을 먼저 완성한다.
- 상태 전이표(2절)와 명령 유효성 표를 유닛 테스트로 우선 검증한다(모든 상태 × 모든 commandId 조합, 총 5×17행 커버).

## 8. 완료 기준 (Definition of Done)

- [ ]  상태 전이표의 모든 케이스가 유닛 테스트로 검증됨
- [ ]  더미 `VoiceCommandEvent` 주입 시, 상태별 유효성 표대로 `ExecutionCommand` 생성/폐기가 정확함
- [ ]  캘리브레이션 9단계가 더미 `RawFaceOrientation`으로 정상 완료되고 `CalibrationProfile`이 스키마대로 생성됨
- [ ]  5개 상태 각각에 대해 오버레이 UI(포인터 표시 방식, 인디케이터)가 명세대로 렌더링됨
- [ ]  DRAGGING 30초 초과 시 자동 취소 동작 확인
- [ ]  LOCKED 상태 수동 해제 버튼 노출 및 동작 확인

## 9. 통합 시 A/B/C팀과 확인할 사항

- A: 좌표계(정규화 0~1) 및 `confidence` 임계값 기준 최종 합의
- B: `commandId` 사전 변경 시 유효성 표 동기화 프로세스 합의
- C: 드래그 자동 취소 타임아웃 값(기본 30초) 및 실행 결과 실패 사유(`errorReason`) 코드 목록 합의