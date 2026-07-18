package com.madcamp.handsfree.debug

import com.madcamp.handsfree.contract.CalibrationProfile
import com.madcamp.handsfree.contract.CalibrationProfile.Level
import com.madcamp.handsfree.contract.CalibrationProfile.ReferencePoint

/**
 * D의 캘리브레이션 플로우를 기다리지 않기 위한 더미 프로파일 (A 명세 §5).
 *
 * **통합 시 삭제한다.** 실제 프로파일은 D가 9개 기준점을 수집해 만든다.
 *
 * 범위 값은 "거치된 기기를 편하게 볼 때 사람이 실제로 고개를 돌리는 정도"로 잡았다.
 * 너무 좁게 잡으면 조금만 움직여도 화면 끝에 붙고, 넓게 잡으면 목을 크게 돌려야
 * 구석에 닿는다. 실기기에서 조정이 필요한 값이다.
 */
object MockCalibration {

    fun profile(
        sensitivity: Level = Level.MID,
        smoothing: Level = Level.MID,
    ) = CalibrationProfile(
        profileId = "mock_dev",
        referencePoints = REFERENCE_POINTS,
        faceRangeYawMin = -25f,
        faceRangeYawMax = 25f,
        faceRangePitchMin = -18f,
        faceRangePitchMax = 18f,
        sensitivityLevel = sensitivity,
        smoothingLevel = smoothing,
    )

    /**
     * 9개 기준점(3×3). A는 이 값을 매핑에 쓰지 않지만(OPEN_ISSUES #6),
     * 스키마가 요구하므로 형태만 맞춰 둔다.
     */
    private val REFERENCE_POINTS = listOf(
        ReferencePoint(-25f, 18f), ReferencePoint(0f, 18f), ReferencePoint(25f, 18f),
        ReferencePoint(-25f, 0f), ReferencePoint(0f, 0f), ReferencePoint(25f, 0f),
        ReferencePoint(-25f, -18f), ReferencePoint(0f, -18f), ReferencePoint(25f, -18f),
    )
}
