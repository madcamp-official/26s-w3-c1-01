package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.Level
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.orchestrator.calibration.CalibrationPoint

/**
 * RawFaceOrientation → PointerFrame. A파트 명세 §2의 3~6단계.
 *
 * pitch 결합 보정 → 정규화 → 시선 보조 혼합 → 감도 스케일링 → smoothing → 클램핑 순서다.
 * **smoothing을 감도 스케일링보다 먼저 걸면 안 된다** — 감도를 높이면 떨림도
 * 같이 증폭돼서 필터를 통과한 의미가 사라진다.
 * **pitch 결합 보정은 반드시 정규화보다 앞이어야 한다** — 각도 단위로 재서 각도
 * 단위로 빼는 값이라 화면 좌표로 바꾼 뒤에 적용하면 보정 범위와 어긋난다.
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

    /**
     * yaw² 에 비례하는 pitch 결합을 되돌리는 계수. 캘리브레이션에서 산출한다.
     *
     * 실기기에서 고개를 좌우로만 돌렸는데 pitch가 **양쪽 끝에서 똑같이 4~5도**
     * 올라갔다. 좌우 대칭이라 부호 문제가 아니라 결합이고, 결과가 아래로 볼록한 호였다.
     * 원인은 큰 각도에서 MediaPipe의 자세 추정이 흔들리는 것 + 사람이 고개를 돌릴 때
     * 턱을 함께 드는 습관이 섞인 것으로 보인다 — 어느 쪽이든 사용자·자세마다 다르다.
     */
    private var pitchCouplingK = 0f
    private var yawCenter = 0f

    /**
     * 보정을 적용할 dYaw의 상·하한(캘리브레이션에서 실제로 측정한 범위).
     *
     * **이차식을 측정 범위 밖으로 외삽하면 안 된다.** 실기기에서 캘리브레이션 때보다
     * 고개를 더 돌리자 보정량이 제곱으로 자라 1.5배 과보정됐고, 호가 반대 방향
     * (위로 볼록)으로 뒤집혔다. 범위 밖에서는 보정량을 끝값으로 고정한다.
     */
    private var couplingYawMin = 0f
    private var couplingYawMax = 0f


    /** D가 프로파일을 갱신하면(재보정 포함) 즉시 반영한다 */
    fun updateProfile(newProfile: CalibrationProfile) {
        profile = newProfile
        smoother.setLevel(newProfile.smoothingLevel)
        computePitchCoupling(newProfile)
        // 매핑 기준이 통째로 바뀌었으므로 이전 좌표에서 이어가면 안 된다
        smoother.reset()
    }

    /**
     * CENTER·LEFT·RIGHT 기준점으로 pitch 결합량을 잰다.
     *
     * 이 세 점은 화면에서 **같은 높이**(y=0.5)라 사용자가 pitch를 바꿀 이유가 없다.
     * 그런데도 pitch가 다르면 그 차이가 곧 결합량이다. 세 점을 지나는 대칭
     * 이차식으로 근사한다.
     *
     * **이건 9점 보간이 아니다**(OPEN_ISSUES #6은 그대로 유효하다). 정규화는 여전히
     * min/max 선형이고, 여기서는 기준점을 결합 계수 하나를 뽑는 데만 쓴다.
     */
    private fun computePitchCoupling(p: CalibrationProfile) {
        val center = p.referencePoints.getOrNull(CalibrationPoint.CENTER.ordinal)
        val left = p.referencePoints.getOrNull(CalibrationPoint.LEFT.ordinal)
        val right = p.referencePoints.getOrNull(CalibrationPoint.RIGHT.ordinal)
        if (center == null || left == null || right == null) {
            pitchCouplingK = 0f
            return
        }

        yawCenter = center.yaw
        val dl = left.yaw - center.yaw
        val dr = right.yaw - center.yaw
        // LEFT가 항상 음수 쪽이라는 보장이 없다(부호 규약이 바뀌면 뒤집힌다)
        couplingYawMin = minOf(dl, dr)
        couplingYawMax = maxOf(dl, dr)

        val spread = (dl * dl + dr * dr) / 2f
        // 좌우로 거의 안 돌린 보정이면 나눗셈이 폭발한다. 보정을 포기하는 쪽이 안전하다
        if (spread < 1f) {
            pitchCouplingK = 0f
            return
        }

        val excess = ((left.pitch - center.pitch) + (right.pitch - center.pitch)) / 2f
        pitchCouplingK = excess / spread
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

        // 1) 고개를 좌우로 돌릴 때 딸려 들어온 pitch를 먼저 걷어낸다.
        //    이걸 정규화 뒤에 하면 이미 화면 좌표로 변환된 값을 손대는 셈이라
        //    보정 범위와 어긋난다 — 반드시 각도 단계에서 처리한다.
        //    보정은 측정 범위 안에서만 이차식을 따르고, 밖에서는 끝값으로 고정한다.
        //    클램핑을 빼면 캘리브레이션보다 고개를 더 돌렸을 때 보정이 제곱으로
        //    자라서 과보정되고, 호가 반대 방향으로 뒤집힌다.
        val dYaw = (raw.yaw - yawCenter).coerceIn(couplingYawMin, couplingYawMax)
        val correctedPitch = raw.pitch - pitchCouplingK * dYaw * dYaw

        // 2) 캘리브레이션 범위 기준 선형 정규화 (9점 보간은 하지 않는다 — OPEN_ISSUES #6)
        var nx = normalize(raw.yaw, p.faceRangeYawMin, p.faceRangeYawMax)
        // pitch는 위를 볼수록 +인데 화면 y는 아래로 갈수록 +라서 뒤집는다
        var ny = 1f - normalize(correctedPitch, p.faceRangePitchMin, p.faceRangePitchMax)

        // 3) 시선 보조. 얼굴을 크게 안 돌려도 눈만 굴려 미세 조정할 수 있게 한다.
        //    가중치를 받을 경로가 계약에 없어 상수로 뒀다 (OPEN_ISSUES #1)
        //
        //    **가로에만 건다.** 실측에서 eyeOffsetY는 시선이 아니라 원근 아티팩트였다 —
        //    고개를 좌우로 돌리면 눈을 비스듬히 보게 되면서 눈동자의 세로 중심 계산이
        //    좌우 양쪽에서 똑같이 틀어졌고, 그게 호의 35%를 만들고 있었다.
        //    세로는 홍채가 움직일 수 있는 폭 자체가 좁아서 신호 대비 잡음이 나쁘다.
        nx += raw.eyeOffsetX * gazeAssistWeight

        // 4) 감도. 화면 중앙을 기준으로 확대해야 정면을 봤을 때 포인터가 중앙에 온다
        val gain = gainFor(p.sensitivityLevel)
        nx = CENTER + (nx - CENTER) * gain
        ny = CENTER + (ny - CENTER) * gain

        // 5) smoothing
        val (sx, sy) = smoother.smooth(nx, ny)

        // 6) 캘리브레이션 범위를 벗어난 각도는 화면 경계에 붙인다
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
