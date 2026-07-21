# Part D Spec — 현재 상태 머신

## 책임

D는 전역 상태를 소유하고, 음성 명령을 게이트키핑하며, 유효한 OS 실행 명령만 C로 보낸다.

## 상태

| 상태 | 설명 |
| --- | --- |
| CALIBRATING | 보정 중. 모든 음성 명령 거부 |
| ACTIVE | 터치/뒤로가기/스크롤/좌우 이동/잠금 처리 |
| LOCKED | 잠금 해제만 처리 |

## 명령

| CommandId | 처리 |
| --- | --- |
| TOUCH | C로 전달 |
| BACK | C로 전달 |
| SCROLL_DOWN | C로 전달 |
| SCROLL_UP | C로 전달 |
| NEXT | C로 전달 |
| PREV | C로 전달 |
| LOCK | 상태만 LOCKED로 전이 |
| UNLOCK | 상태만 ACTIVE로 전이 |

`EXIT`는 B-D 어댑터에서 직접 처리하는 특수 명령이며 D 상태 머신에 포함하지 않는다.

## Reject Reason

| 사유 | 의미 |
| --- | --- |
| INVALID_IN_STATE | 현재 상태에서 유효하지 않은 명령 |
| NEED_UNLOCK | LOCKED 상태에서 UNLOCK 외 명령 수신 |

## 테스트 기준

`CommandGateTest`는 3 상태 × 8 명령 = 24 조합을 전수 검증한다.
