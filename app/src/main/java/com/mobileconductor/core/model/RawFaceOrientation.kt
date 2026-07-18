package com.mobileconductor.core.model

/**
 * A → D (캘리브레이션 중). 얼굴 방향 원시값.
 *
 * D는 캘리브레이션 단계에서 이 값을 일정 시간 평균하여 9개 기준점을 기록한다(명세 forD 3절).
 *
 * @param yaw 얼굴 좌우 회전(도). 단위(도 vs 라디안)는 통합 시 A와 합의(명세 forD 9절).
 * @param pitch 얼굴 상하 회전(도).
 * @param faceDetected 얼굴 검출 여부. false가 5초 지속되면 해당 단계 재시도.
 * @param confidence 추정 신뢰도 [0.0, 1.0]
 * @param timestamp epoch millis
 */
data class RawFaceOrientation(
    val yaw: Float,
    val pitch: Float,
    val faceDetected: Boolean,
    val confidence: Float,
    val timestamp: Long
)
