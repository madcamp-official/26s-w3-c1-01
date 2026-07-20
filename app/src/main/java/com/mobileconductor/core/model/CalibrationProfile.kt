package com.mobileconductor.core.model

/** 사용자가 선택하는 3단계 레벨 (이동 속도 / 스무딩 강도). */
enum class Level { LOW, MID, HIGH }

/** 9개 기준점별 얼굴 방향 값. */
data class FaceOrientationValue(
    val yaw: Float,
    val pitch: Float
)

/**
 * D → A. 캘리브레이션 산출물(기기 로컬 저장, 명세 forD 3절 스키마).
 *
 * referencePoints는 정확히 9개(중앙 + 상/하/좌/우 + 대각선 4)여야 한다.
 * faceRange*는 9개 값의 min/max로 산출한다.
 *
 * 시간 필드는 ISO-8601 문자열(예: "2026-07-18T00:00:00Z").
 */
data class CalibrationProfile(
    val profileId: String,
    val referencePoints: List<FaceOrientationValue>,
    val faceRangeYawMin: Float,
    val faceRangeYawMax: Float,
    val faceRangePitchMin: Float,
    val faceRangePitchMax: Float,
    val sensitivityLevel: Level,
    val smoothingLevel: Level,
    val createdAt: String,
    val updatedAt: String
) {
    init {
        require(referencePoints.size == REFERENCE_POINT_COUNT) {
            "referencePoints must have exactly $REFERENCE_POINT_COUNT entries, was ${referencePoints.size}"
        }
    }

    companion object {
        const val REFERENCE_POINT_COUNT = 9
    }
}
