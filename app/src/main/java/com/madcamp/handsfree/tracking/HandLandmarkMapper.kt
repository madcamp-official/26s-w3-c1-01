package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.Level
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation

/**
 * HAND 모드의 매핑기. A의 [PointerMapper]에 대응하되 **훨씬 단순하다.**
 *
 * 손은 이미 화면 좌표에 가까운 2D 위치 신호라 얼굴처럼 각도→좌표 변환이 없다.
 * 그래서 A의 pitch 결합 보정·시선 보조는 여기 없다 — 손에는 그런 아티팩트가 없다.
 *
 * ## 계약 재사용 (MOTION_CAPTURE_PLAN 축 1)
 * `HandTracker`는 손의 **화면 방향으로 보정된 위치**(hx, hy ∈ 0~1)를
 * [RawFaceOrientation]의 `yaw`/`pitch` 슬롯에 실어 보낸다. 그래서:
 * - 캘리브레이션은 A와 **동일한 9점 수집**을 거쳐 `faceRangeYaw`=x 도달범위,
 *   `faceRangePitch`=y 도달범위를 만든다([CalibrationController]는 min/max만 보므로 무변경).
 * - 여기서는 그 범위로 hx/hy를 선형 정규화한다.
 *
 * **부호/미러링은 [HandTracker]가 이미 흡수했다.** yaw 슬롯이 커지면 화면 오른쪽,
 * pitch 슬롯이 커지면 화면 아래다. 그래서 A의 PointerMapper와 달리 여기서
 * `1f - ny` 뒤집기를 하지 않는다.
 */
class HandLandmarkMapper {

    private var profile: CalibrationProfile? = null
    private var smoother = Smoother(Level.MID)

    /** D가 프로파일을 갱신하면(재보정 포함) 즉시 반영한다. A의 PointerMapper와 동일 규약. */
    fun updateProfile(newProfile: CalibrationProfile) {
        profile = newProfile
        smoother.setLevel(newProfile.smoothingLevel)
        // 매핑 기준이 통째로 바뀌었으므로 이전 좌표에서 이어가면 안 된다
        smoother.reset()
    }

    fun onHandLost() {
        smoother.reset()
    }

    /**
     * @return 프로파일이 없거나 손 미검출이면 화면 중앙 + faceDetected=false.
     *   null을 반환하지 않는 것은 A의 [PointerFrame] 규약과 동일하다(OPEN_ISSUES #3).
     *   `faceDetected` 필드를 HAND 모드에서는 "손 검출됨"으로 재사용한다(SPEC §9-A, MVP 결정).
     */
    fun map(raw: RawFaceOrientation, lowLight: Boolean): PointerFrame {
        val p = profile
        if (p == null || !raw.faceDetected) {
            return PointerFrame(
                timestamp = raw.timestamp,
                x = CENTER,
                y = CENTER,
                faceDetected = false,
                confidence = raw.confidence,
                lowLight = lowLight,
            )
        }

        // 1) 캘리브레이션 도달범위 기준 선형 정규화 (뒤집기 없음 — HandTracker가 이미 화면 방향)
        var nx = normalize(raw.yaw, p.faceRangeYawMin, p.faceRangeYawMax)
        var ny = normalize(raw.pitch, p.faceRangePitchMin, p.faceRangePitchMax)

        // 2) 감도. 화면 중앙 기준으로 확대해야 정면 위치에서 포인터가 중앙에 온다.
        //    A의 PointerMapper.gainFor와 동일한 값을 쓴다(체감 3단계 일관성).
        val gain = gainFor(p.sensitivityLevel)
        nx = CENTER + (nx - CENTER) * gain
        ny = CENTER + (ny - CENTER) * gain

        // 3) smoothing (A의 Smoother 재사용 — 손 랜드마크도 미세 떨림이 있다)
        val (sx, sy) = smoother.smooth(nx, ny)

        // 4) 도달범위를 벗어난 위치는 화면 경계에 붙인다(FR-M-001 클램핑)
        return PointerFrame(
            timestamp = raw.timestamp,
            x = sx.coerceIn(0f, 1f),
            y = sy.coerceIn(0f, 1f),
            faceDetected = true,
            confidence = raw.confidence,
            lowLight = lowLight,
        )
    }

    private fun normalize(value: Float, min: Float, max: Float): Float {
        // 캘리브레이션이 잘못돼 범위가 0이면 0으로 나누게 된다.
        // 중앙에 두는 게 화면 구석에 처박히는 것보다 낫다(A와 동일 처리).
        val range = max - min
        if (range <= 1e-3f) return CENTER
        return (value - min) / range
    }

    private companion object {
        const val CENTER = 0.5f

        // A의 PointerMapper.gainFor와 같은 값. 두 모드의 감도 체감을 맞춘다.
        fun gainFor(level: Level) = when (level) {
            Level.LOW -> 0.8f
            Level.MID -> 1.0f
            Level.HIGH -> 1.4f
        }
    }
}
