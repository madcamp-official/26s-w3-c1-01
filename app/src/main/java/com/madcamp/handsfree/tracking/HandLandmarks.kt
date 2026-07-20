package com.madcamp.handsfree.tracking

/**
 * 한 프레임의 손 랜드마크 스냅샷. **MediaPipe 타입을 여기서 끊어낸다.**
 *
 * [GestureClassifier]가 이 순수 데이터만 보게 해서, MediaPipe `NormalizedLandmark`
 * 없이도 유닛 테스트로 제스처 로직을 전수 검증할 수 있게 한다(D의 CommandGate 테스트와 같은 방식).
 *
 * @param points 랜드마크 21점(손목 0 … 새끼끝 20)의 원시 정규화 좌표. **비어 있으면 손 미검출**
 *   — 분류기는 이때 궤적/유지 상태를 리셋한다. 미러링/회전을 거치지 않은 이미지 공간이라
 *   손 모양(손가락 폄/접힘) 판정에만 쓴다(상대 기하라 미러링 영향 없음).
 * @param screenTipX 검지 끝(#8)의 **화면 방향** x. 포인터와 같은 좌표계(스와이프 방향 판정용).
 * @param screenTipY 검지 끝의 화면 방향 y.
 * @param timestamp 단조 시계(SystemClock.elapsedRealtime) millis. 스와이프 시간창·쿨다운 계산용.
 */
data class HandLandmarks(
    val points: List<Point>,
    val screenTipX: Float,
    val screenTipY: Float,
    val timestamp: Long,
) {
    data class Point(val x: Float, val y: Float, val z: Float)

    val detected: Boolean get() = points.isNotEmpty()

    /** MediaPipe HandLandmarker 랜드마크 인덱스(21점 규약). */
    companion object {
        const val WRIST = 0
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_PIP = 6
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_PIP = 10
        const val MIDDLE_TIP = 12
        const val RING_PIP = 14
        const val RING_TIP = 16
        const val PINKY_PIP = 18
        const val PINKY_TIP = 20
    }
}
