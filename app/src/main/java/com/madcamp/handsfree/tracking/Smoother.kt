package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.Level
import kotlin.math.abs

/**
 * 포인터 떨림 저감 필터.
 *
 * 얼굴 랜드마크 원시값은 가만히 있어도 미세하게 떨린다. 그대로 화면에 그리면
 * 포인터가 부들부들 떨려서 버튼을 못 맞춘다. **smoothing을 빼면 앱이 고장난
 * 것처럼 느껴진다.**
 *
 * 단순 EMA(지수 이동 평균) 대신 적응형으로 만든 이유:
 * 고정 EMA는 강하게 걸면 떨림은 잡히지만 큰 동작이 굼떠지고, 약하게 걸면
 * 반응은 빠른데 떨림이 남는다. 둘 다 "고장난 느낌"이라 어느 쪽으로도 못 간다.
 * 그래서 **작은 움직임(떨림)은 강하게, 큰 움직임(의도적 이동)은 약하게** 거른다.
 */
class Smoother(level: Level) {

    private var baseAlpha = alphaFor(level)
    private var lastX: Float? = null
    private var lastY: Float? = null

    fun setLevel(level: Level) {
        baseAlpha = alphaFor(level)
    }

    /** 프로파일이 바뀌거나 얼굴을 다시 찾았을 때. 이전 위치에서 끌려오는 걸 막는다 */
    fun reset() {
        lastX = null
        lastY = null
    }

    fun smooth(x: Float, y: Float): Pair<Float, Float> {
        val px = lastX
        val py = lastY
        if (px == null || py == null) {
            lastX = x
            lastY = y
            return x to y
        }

        // 이동량이 클수록 alpha를 1에 가깝게 = 필터를 약하게 = 즉각 반응
        val delta = abs(x - px) + abs(y - py)
        val adaptive = (baseAlpha + delta * ADAPT_GAIN).coerceAtMost(1f)

        val nx = px + (x - px) * adaptive
        val ny = py + (y - py) * adaptive
        lastX = nx
        lastY = ny
        return nx to ny
    }

    private companion object {
        /**
         * alpha가 작을수록 강한 필터. 정규화 좌표(0~1) 기준이고 15~30fps를 가정한 값이다.
         * 실기기에서 체감으로 조정해야 한다 — DoD에 "3단계가 체감상 구분됨"이 있다.
         */
        fun alphaFor(level: Level) = when (level) {
            Level.LOW -> 0.55f    // 필터 약함 = 반응 빠름, 떨림 남음
            Level.MID -> 0.30f
            Level.HIGH -> 0.15f   // 필터 강함 = 안정적, 굼뜸
        }

        const val ADAPT_GAIN = 2.0f
    }
}
