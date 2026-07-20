package com.mobileconductor.core.model

/**
 * A → C, D. 매 프레임의 포인터 좌표(정규화 0~1)와 얼굴 검출 상태.
 *
 * 통합 시 A(+lowLight)·C(필드 5개)·D(필드 5개, 순서 다름) 세 벌이 있었고 여기로 합쳤다.
 * **A/C/D 모두 이름 있는 인자로 생성한다** — 위치 인자로 만들면 x/y/timestamp가 전부
 * Float/Long이라 컴파일은 통과하고 좌표만 조용히 틀어진다.
 *
 * 좌표 규약(A가 정하고 통합 시 확정한 것, docs/PART_A_OPEN_ISSUES.md #3/#5):
 * - **null이 되지 않는다.** 프로파일 미수신 시에도 (0.5, 0.5) + faceDetected=false를 보낸다.
 * - 기준은 **현재 회전 상태의 전체 화면**. 회전 보정은 A가 흡수하므로 C/D는 신경 쓰지 않는다.
 * - 미러링: 고개를 사용자 기준 오른쪽으로 돌리면 x가 증가한다.
 *
 * @param x 화면 가로 정규화 좌표 [0.0, 1.0] (좌→우)
 * @param y 화면 세로 정규화 좌표 [0.0, 1.0] (상→하)
 * @param faceDetected 현재 프레임에서 얼굴이 검출되었는지
 * @param confidence 얼굴 검출 신뢰도 [0.0, 1.0].
 *   **현재 A는 항상 1.0을 보낸다** — MediaPipe가 얼굴별 점수를 주지 않는다.
 *   D가 이 값으로 UI를 분기할 계획이면 대신 [lowLight]를 쓸 것(A 핸드오프 §3).
 * @param timestamp 단조 시계(SystemClock.elapsedRealtime) 기준 millis.
 *   **벽시계가 아니다.** 섞으면 D의 순서 판단이 조용히 틀어진다(OPEN_ISSUES #7).
 * @param lowLight 저조도 여부. 계약에 없던 A 확장 필드로, 안내 UI는 D 정책이다.
 *   명세서는 저조도 시 confidence를 낮추라고 했지만 조도와 검출 품질은 다른 신호다.
 */
data class PointerFrame(
    val x: Float,
    val y: Float,
    val faceDetected: Boolean,
    val confidence: Float,
    val timestamp: Long,
    val lowLight: Boolean = false
)
