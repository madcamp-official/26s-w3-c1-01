package com.madcamp.handsfree.tracking

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * 홍채가 눈 안에서 어느 쪽으로 쏠렸는지 계산한다. "시선 보조 입력"의 실체.
 *
 * 얼굴 방향만 쓰면 포인터를 조금 옮기려고 고개를 계속 돌려야 해서 목이 아프다.
 * 눈동자 위치를 소량 섞으면 고개를 고정한 채로 미세 조정이 된다.
 *
 * **이게 ML Kit 대신 MediaPipe를 고른 이유다.** ML Kit Face Detection은 홍채
 * 랜드마크를 주지 않아서 이 계산이 아예 불가능하다.
 */
object EyeOffset {

    /** MediaPipe Face Landmarker의 홍채/눈 랜드마크 인덱스(468번부터가 홍채) */
    private const val LEFT_IRIS = 468
    private const val RIGHT_IRIS = 473
    private const val LEFT_EYE_OUTER = 33
    private const val LEFT_EYE_INNER = 133
    private const val RIGHT_EYE_INNER = 362
    private const val RIGHT_EYE_OUTER = 263
    private const val LEFT_EYE_TOP = 159
    private const val LEFT_EYE_BOTTOM = 145
    private const val RIGHT_EYE_TOP = 386
    private const val RIGHT_EYE_BOTTOM = 374

    /**
     * 눈을 떴다고 볼 최소 종횡비(눈 높이 / 눈 너비).
     * 뜬 눈은 0.25~0.35, 감은 눈은 0.05 아래로 떨어진다.
     *
     * 0.15로 잡았더니 깜빡임 중간의 "반쯤 감긴" 프레임이 통과해서 여전히 튀었다.
     * 그 구간에서는 홍채가 눈꺼풀에 가려 랜드마크가 엉뚱한 곳을 가리킨다.
     * 감기 시작하면 일찍 버리는 게 낫다.
     */
    private const val OPEN_EYE_RATIO = 0.22f

    /**
     * 전면 카메라 영상은 좌우가 뒤집혀 있다. 머리 방향은 HeadPose.YAW_SIGN에서
     * 이미 뒤집었는데, 홍채 좌표는 영상 좌표를 그대로 쓰기 때문에 여기서 따로
     * 뒤집어야 한다. 안 뒤집으면 눈을 오른쪽으로 굴렸는데 포인터가 왼쪽으로 간다.
     *
     * 세로는 뒤집지 않는다 — 영상의 y도 화면의 y도 아래로 갈수록 증가한다.
     */
    private const val GAZE_X_SIGN = -1f

    /**
     * @param x,y -1.0~1.0. 0이 눈 중앙.
     * @param eyesOpen 눈을 감고 있으면 false — 이때 x,y는 신뢰할 수 없다.
     */
    data class Gaze(val x: Float, val y: Float, val eyesOpen: Boolean) {
        companion object {
            val CLOSED = Gaze(0f, 0f, eyesOpen = false)
        }
    }

    fun from(landmarks: List<NormalizedLandmark>): Gaze {
        // 홍채 랜드마크가 없는 모델이면 시선 보조만 빠지고 나머지는 정상 동작한다
        if (landmarks.size <= RIGHT_IRIS) return Gaze.CLOSED

        val left = eyeOf(
            landmarks, LEFT_IRIS,
            LEFT_EYE_OUTER, LEFT_EYE_INNER, LEFT_EYE_TOP, LEFT_EYE_BOTTOM,
        )
        val right = eyeOf(
            landmarks, RIGHT_IRIS,
            RIGHT_EYE_INNER, RIGHT_EYE_OUTER, RIGHT_EYE_TOP, RIGHT_EYE_BOTTOM,
        )

        // 한쪽만 감은 경우(윙크, 조명으로 한쪽 랜드마크가 튄 경우)에는 뜬 쪽만 쓴다
        return when {
            left.eyesOpen && right.eyesOpen ->
                Gaze((left.x + right.x) / 2f, (left.y + right.y) / 2f, true)
            left.eyesOpen -> left
            right.eyesOpen -> right
            else -> Gaze.CLOSED
        }
    }

    private fun eyeOf(
        lm: List<NormalizedLandmark>,
        iris: Int,
        cornerA: Int,
        cornerB: Int,
        top: Int,
        bottom: Int,
    ): Gaze {
        val width = abs(lm[cornerB].x() - lm[cornerA].x())
        val height = abs(lm[bottom].y() - lm[top].y())

        // 눈을 감으면 height가 0에 수렴한다. 예전 코드는 이 값으로 나눠서
        // 깜빡일 때마다 세로 오프셋이 ±1로 폭발했고, 그게 "깜빡이면 포인터가 튀는"
        // 증상의 정체였다. 감은 눈은 계산하지 않고 감았다고 알린다.
        if (width <= 1e-5f || height / width < OPEN_EYE_RATIO) return Gaze.CLOSED

        val centerX = (lm[cornerA].x() + lm[cornerB].x()) / 2f
        val centerY = (lm[top].y() + lm[bottom].y()) / 2f

        // 눈 크기로 나눠 정규화한다. 안 나누면 얼굴이 카메라에 가까울수록
        // 같은 시선인데 값이 커져서 포인터가 튄다
        val ox = (lm[iris].x() - centerX) / (width / 2f) * GAZE_X_SIGN
        val oy = (lm[iris].y() - centerY) / (height / 2f)

        return Gaze(ox.coerceIn(-1f, 1f), oy.coerceIn(-1f, 1f), eyesOpen = true)
    }
}
