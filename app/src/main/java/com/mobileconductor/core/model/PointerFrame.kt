package com.mobileconductor.core.model

/**
 * A → D. 매 프레임의 포인터 좌표(정규화 0~1)와 얼굴 검출 상태.
 *
 * 좌표계·confidence 임계값의 최종 기준은 통합 시 A와 합의한다(명세 forD 9절).
 *
 * @param x 화면 가로 정규화 좌표 [0.0, 1.0] (좌→우)
 * @param y 화면 세로 정규화 좌표 [0.0, 1.0] (상→하)
 * @param faceDetected 현재 프레임에서 얼굴이 검출되었는지
 * @param confidence 추정 신뢰도 [0.0, 1.0]
 * @param timestamp epoch millis
 */
data class PointerFrame(
    val x: Float,
    val y: Float,
    val faceDetected: Boolean,
    val confidence: Float,
    val timestamp: Long
)
