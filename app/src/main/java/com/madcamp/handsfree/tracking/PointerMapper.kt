package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.Level
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation

/**
 * RawFaceOrientation → PointerFrame. A파트 명세 §2의 3~6단계.
 *
 * 정규화 → 시선 보조 혼합 → 감도 스케일링 → smoothing → 클램핑 순서로 처리한다.
 * **smoothing을 감도 스케일링보다 먼저 걸면 안 된다** — 감도를 높이면 떨림도
 * 같이 증폭돼서 필터를 통과한 의미가 사라진다.
 */
class PointerMapper {

    private var profile: CalibrationProfile? = null
    private var smoother = Smoother(Level.MID)

    /**
     * 시선 보조 가중치. 계약에 전달 경로가 없어 여기 둔다 (OPEN_ISSUES #1).
     *
     * 실기기에서 몇으로 두는 게 맞는지는 사람이 써 보고 정해야 하는 값이라
     * 디버그 화면에서 바꿀 수 있게 열어놨다. 값이 정해지면 상수로 굳히거나
     * CalibrationProfile에 필드를 추가한다.
     */
    @Volatile
    var gazeAssistWeight: Float = DEFAULT_GAZE_ASSIST_WEIGHT

    /** D가 프로파일을 갱신하면(재보정 포함) 즉시 반영한다 */
    fun updateProfile(newProfile: CalibrationProfile) {
        profile = newProfile
        smoother.setLevel(newProfile.smoothingLevel)
        // 매핑 기준이 통째로 바뀌었으므로 이전 좌표에서 이어가면 안 된다
        smoother.reset()
    }

    fun onFaceLost() {
        smoother.reset()
    }

    /**
     * @return 프로파일이 없으면 화면 중앙 + faceDetected=false.
     *         null을 반환하지 않는 이유는 OPEN_ISSUES #3 참고.
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

        // 1) 캘리브레이션 범위 기준 선형 정규화 (9점 보간은 하지 않는다 — OPEN_ISSUES #6)
        var nx = normalize(raw.yaw, p.faceRangeYawMin, p.faceRangeYawMax)
        // pitch는 위를 볼수록 +인데 화면 y는 아래로 갈수록 +라서 뒤집는다
        var ny = 1f - normalize(raw.pitch, p.faceRangePitchMin, p.faceRangePitchMax)

        // 2) 시선 보조. 얼굴을 크게 안 돌려도 눈만 굴려 미세 조정할 수 있게 한다.
        //    가중치를 받을 경로가 계약에 없어 상수로 뒀다 (OPEN_ISSUES #1)
        nx += raw.eyeOffsetX * gazeAssistWeight
        ny += raw.eyeOffsetY * gazeAssistWeight

        // 3) 감도. 화면 중앙을 기준으로 확대해야 정면을 봤을 때 포인터가 중앙에 온다
        val gain = gainFor(p.sensitivityLevel)
        nx = CENTER + (nx - CENTER) * gain
        ny = CENTER + (ny - CENTER) * gain

        // 4) smoothing
        val (sx, sy) = smoother.smooth(nx, ny)

        // 5) 캘리브레이션 범위를 벗어난 각도는 화면 경계에 붙인다
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
        // 이 경우 포인터를 중앙에 두는 게 화면 구석에 처박히는 것보다 낫다
        val range = max - min
        if (range <= 1e-3f) return CENTER
        return (value - min) / range
    }

    private companion object {
        const val CENTER = 0.5f

        /**
         * 0.2로 시작했더니 실기기에서 "눈을 굴려도 아무 일도 안 일어난다"는
         * 평가가 나왔다. 눈동자 이동폭이 화면의 10%밖에 안 되는데, 그 정도
         * 작은 변화는 smoothing 필터가 떨림으로 보고 깎아내기까지 한다.
         *
         * 올릴수록 홍채 랜드마크의 미세한 떨림도 같이 증폭된다 — 무한정 못 올린다.
         */
        const val DEFAULT_GAZE_ASSIST_WEIGHT = 0.5f

        fun gainFor(level: Level) = when (level) {
            Level.LOW -> 0.8f    // 고개를 많이 돌려야 끝까지 간다 = 정밀하지만 피곤
            Level.MID -> 1.0f
            Level.HIGH -> 1.4f   // 조금만 돌려도 크게 움직인다 = 편하지만 부정확
        }
    }
}
