package com.mobileconductor.core.model

/**
 * A → D (캘리브레이션 중). 매핑을 거치지 않은 얼굴 방향 원시값.
 *
 * D는 캘리브레이션 단계에서 이 값을 일정 시간 평균해 9개 기준점을 기록한다(명세 forD 3절).
 * [PointerFrame]과 달리 CalibrationProfile이 없어도 방출된다 — 애초에 프로파일을
 * 만들기 위한 데이터라서 그렇다.
 *
 * @param yaw 얼굴 좌우 회전. **단위는 도(degree)** — 통합 시 A쪽으로 확정했다(명세 forD 9절).
 *   +가 사용자 기준 오른쪽.
 * @param pitch 얼굴 상하 회전(도). +가 위.
 * @param eyeOffsetX 눈동자가 눈 중심에서 벗어난 정도 [-1.0, 1.0].
 *   A의 시선 보조에만 쓰이고 D의 기준점 수집에는 쓰이지 않아 기본값을 뒀다.
 * @param eyeOffsetY 위와 같음(세로).
 * @param faceDetected 얼굴 검출 여부. false가 5초 지속되면 D가 해당 단계를 재시도.
 * @param confidence 검출 신뢰도 [0.0, 1.0]. [PointerFrame.confidence]와 같은 한계가 있다.
 * @param timestamp 단조 시계(SystemClock.elapsedRealtime) 기준 millis
 */
data class RawFaceOrientation(
    val yaw: Float,
    val pitch: Float,
    val faceDetected: Boolean,
    val confidence: Float,
    val timestamp: Long,
    val eyeOffsetX: Float = 0f,
    val eyeOffsetY: Float = 0f
)
