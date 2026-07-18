package com.madcamp.handsfree.tracking

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * MediaPipe가 주는 4x4 얼굴 변환 행렬에서 yaw/pitch를 뽑는다.
 *
 * MediaPipe는 랜드마크 좌표만 주는 게 아니라 얼굴의 3D 자세를 담은
 * facialTransformationMatrix를 함께 준다. 랜드마크 몇 개의 위치 차이로 각도를
 * 직접 계산하는 것보다 이쪽이 훨씬 안정적이다 — 표정이 바뀌어도 자세 추정이
 * 흔들리지 않는다.
 */
object HeadPose {

    /**
     * 부호 규약을 여기 한 곳에 모아뒀다.
     *
     * **실기기 없이는 이 부호가 맞는지 확인할 방법이 없다.** 카메라 방향, 기기
     * 제조사, MediaPipe 버전에 따라 뒤집힐 수 있어서, 처음 실기기에 올렸을 때
     * "고개를 오른쪽으로 돌렸는데 포인터가 왼쪽으로 간다"면 여기 상수만 뒤집으면
     * 된다. 매핑 로직 안에 부호를 흩뿌려 놓으면 이 수정이 지옥이 된다.
     *
     * 목표 규약(OPEN_ISSUES #5): 사용자가 고개를 오른쪽으로 돌리면 yaw가 +.
     * 고개를 들면 pitch가 +.
     */
    const val YAW_SIGN = -1f
    const val PITCH_SIGN = -1f

    /**
     * @param matrix MediaPipe FaceLandmarkerResult의 4x4 변환 행렬. **열 우선(column-major)** 16개.
     * @return yaw(좌우), pitch(상하) — 단위는 도(degree)
     */
    fun fromTransformMatrix(matrix: FloatArray): Pair<Float, Float> {
        // 열 우선 배열에서 R[row][col] = matrix[col * 4 + row]
        fun r(row: Int, col: Int) = matrix[col * 4 + row]

        // R = Rz * Ry * Rx 분해. pitch는 짐벌락을 피하려고 asin 대신
        // atan2(-r20, hypot(r21, r22)) 형태를 쓴다.
        val sy = hypot(r(2, 1), r(2, 2))
        val yawRad: Float
        val pitchRad: Float

        if (sy > 1e-6f) {
            pitchRad = atan2(r(2, 1), r(2, 2))
            yawRad = atan2(-r(2, 0), sy)
        } else {
            // 특이점(정면에서 크게 벗어난 자세). 이 구간은 어차피 캘리브레이션
            // 범위 밖이라 클램핑되므로 근사값으로 충분하다.
            pitchRad = 0f
            yawRad = asin(-r(2, 0).coerceIn(-1f, 1f))
        }

        val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat() * YAW_SIGN
        val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat() * PITCH_SIGN

        return yawDeg to normalizePitch(pitchDeg)
    }

    /**
     * pitch는 정면일 때 ±180 근처에서 나오는 경우가 있다(행렬 분해의 성질).
     * 0 근처로 접어주지 않으면 고개를 살짝 들었을 뿐인데 값이 179 → -179로
     * 튀면서 포인터가 화면 위아래로 순간이동한다.
     */
    private fun normalizePitch(deg: Float): Float = when {
        deg > 90f -> deg - 180f
        deg < -90f -> deg + 180f
        else -> deg
    }
}
