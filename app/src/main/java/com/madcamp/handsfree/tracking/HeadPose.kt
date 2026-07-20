package com.madcamp.handsfree.tracking

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * MediaPipe가 주는 4x4 얼굴 변환 행렬에서 yaw/pitch를 뽑는다.
 *
 * MediaPipe는 랜드마크 좌표만 주는 게 아니라 얼굴의 3D 자세를 담은
 * facialTransformationMatrix를 함께 준다. 랜드마크 몇 개의 위치 차이로 각도를
 * 직접 계산하는 것보다 이쪽이 훨씬 안정적이다 — 표정이 바뀌어도 자세 추정이
 * 흔들리지 않는다.
 *
 * ## 오일러 분해를 쓰지 않는 이유 (실기기에서 잡은 문제)
 *
 * 처음에는 회전행렬을 오일러 각(Rz·Ry·Rx)으로 분해해 yaw/pitch를 뽑았다.
 * 그랬더니 **고개를 좌우로만 돌려도 포인터가 호를 그리며 움직였다.**
 *
 * 오일러 분해는 축 순서에 의존해서, 이미 고개가 숙여진 상태(pitch≠0)에서 좌우로
 * 돌리면 분해된 pitch 값이 yaw에 따라 같이 변한다. 폰을 내려다보는 게 이 앱의
 * 기본 자세라 pitch 중심이 +15도쯤이고(실측), 그 자세에서 증상이 뚜렷했다.
 *
 * 지금은 **얼굴이 향하는 방향 벡터를 직접 구면좌표로 바꾼다.** 좌우 회전은
 * forward 벡터의 x/z만 바꾸고 y는 건드리지 않으므로 pitch가 섞이지 않는다.
 * **오일러 분해로 되돌리면 호 문제가 그대로 돌아온다.**
 */
object HeadPose {

    /**
     * 부호 규약을 여기 한 곳에 모아뒀다.
     *
     * **실기기 없이는 이 부호가 맞는지 확인할 방법이 없다.** 카메라 방향, 기기
     * 제조사, MediaPipe 버전에 따라 뒤집힐 수 있어서, "고개를 오른쪽으로 돌렸는데
     * 포인터가 왼쪽으로 간다"면 여기 상수만 뒤집으면 된다. 매핑 로직 안에 부호를
     * 흩뿌려 놓으면 이 수정이 지옥이 된다.
     *
     * 목표 규약(OPEN_ISSUES #5): 사용자가 고개를 오른쪽으로 돌리면 yaw가 +.
     * 고개를 들면 pitch가 +.
     *
     * **PITCH_SIGN이 +1인 건 forward 벡터 방식으로 바꾸면서 계산된 값이다.**
     * 오일러 분해 시절에는 -1이었다. 순수 X축 회전 φ에 대해 예전 방식은 +φ를,
     * 지금 방식은 -φ를 내놓기 때문에 부호가 한 번 뒤집힌다.
     */
    const val YAW_SIGN = -1f
    const val PITCH_SIGN = 1f

    /**
     * @param matrix MediaPipe FaceLandmarkerResult의 4x4 변환 행렬. **열 우선(column-major)** 16개.
     * @return yaw(좌우), pitch(상하) — 단위는 도(degree)
     */
    fun fromTransformMatrix(matrix: FloatArray): Pair<Float, Float> {
        // 열 우선 배열에서 R[row][col] = matrix[col * 4 + row]
        fun r(row: Int, col: Int) = matrix[col * 4 + row]

        // 회전행렬의 3번째 열 = 얼굴 정면(코가 향하는) 방향 벡터.
        // 캐노니컬 페이스 모델의 +Z가 얼굴 바깥을 향한다.
        val fx = r(0, 2)
        val fy = r(1, 2)
        val fz = r(2, 2)

        // 수평면에 투영한 길이. 좌우 회전과 무관하게 일정하다 —
        // 이 값으로 나누는 게 아니라 pitch의 밑변으로 쓰기 때문에 pitch가 yaw와 분리된다.
        val horizontal = hypot(fx, fz)

        val yawRad = atan2(fx, fz)
        val pitchRad = atan2(fy, horizontal)

        val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat() * YAW_SIGN
        val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat() * PITCH_SIGN

        // pitch는 구조적으로 ±90도를 넘을 수 없어서(atan2의 밑변이 항상 ≥0)
        // 오일러 분해 시절 필요했던 ±180 접기 보정이 사라졌다.
        return yawDeg to pitchDeg
    }
}
