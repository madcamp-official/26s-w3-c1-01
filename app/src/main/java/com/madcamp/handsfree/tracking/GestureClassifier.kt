package com.madcamp.handsfree.tracking

import kotlin.math.abs
import kotlin.math.hypot

/**
 * 손 랜드마크 스트림 → 이산 [HandGesture] 이벤트. **이 파일이 Phase 2의 핵심 난이도다.**
 *
 * 순수 로직 + 결정적 상태 머신이라 [HandLandmarks] 시퀀스를 먹여 유닛 테스트로 전수
 * 검증한다(MediaPipe 의존 없음). 상태(ACTIVE/PAUSED…)를 모르고, 명령 매핑도 하지 않는다 —
 * "무슨 손 동작이 일어났나"만 판정한다.
 *
 * ## 판정 두 갈래
 * - **정적(손 모양)**: 손이 **가만히** 있을 때 같은 모양이 [MIN_HOLD_FRAMES] 연속 유지되면
 *   한 번 발화(유지 중 반복 발화 안 함). 움직이는 중에는 누적하지 않는다 — 포인터를
 *   옮기는 것과 제스처를 구분하는 장치.
 * - **동적(스와이프)**: 검지 끝이 [SWIPE_MAX_MS] 안에 [SWIPE_MIN_DISTANCE] 이상 이동하면
 *   방향으로 판정. **정적보다 우선**(SPEC §4.4) — 움직임이 크면 손 모양은 무시한다.
 *
 * 임계값은 전부 실기기 튜닝 대상이다(SPEC §11-4). 오탐보다 미탐을 택하는 쪽으로 잡았다.
 */
class GestureClassifier {

    private val trail = ArrayDeque<Sample>()

    private var heldShape: HandShape? = null
    private var heldFrames = 0
    private var firedThisHold = false
    private var lastTipX = Float.NaN
    private var lastTipY = Float.NaN

    private data class Sample(val x: Float, val y: Float, val t: Long)

    /** @return 이번 프레임에 확정된 제스처, 없으면 null. */
    fun onFrame(frame: HandLandmarks): HandGesture? {
        if (!frame.detected) {
            reset()
            return null
        }

        // 1) 스와이프 궤적 갱신 + 판정 (정적보다 먼저 — 움직임이 크면 손 모양을 무시한다)
        trail.addLast(Sample(frame.screenTipX, frame.screenTipY, frame.timestamp))
        while (trail.isNotEmpty() && frame.timestamp - trail.first().t > SWIPE_MAX_MS) {
            trail.removeFirst()
        }
        detectSwipe()?.let { swipe ->
            // 스와이프가 나면 궤적과 정적 유지를 모두 리셋한다(같은 동작이 곧바로 재발화하지 않게)
            trail.clear()
            resetHold()
            lastTipX = frame.screenTipX
            lastTipY = frame.screenTipY
            return swipe
        }

        // 2) 프레임 간 이동량. 손이 움직이는 중이면 정적 유지를 리셋한다.
        val moving = if (lastTipX.isNaN()) false
        else hypot(frame.screenTipX - lastTipX, frame.screenTipY - lastTipY) > STILL_THRESHOLD
        lastTipX = frame.screenTipX
        lastTipY = frame.screenTipY
        if (moving) {
            resetHold()
            return null
        }

        // 3) 손 모양 판정 + 유지 게이트
        val shape = detectShape(frame.points)
        if (shape == HandShape.UNKNOWN) {
            resetHold()
            return null
        }
        if (shape == heldShape) {
            heldFrames++
        } else {
            heldShape = shape
            heldFrames = 1
            firedThisHold = false
        }
        if (heldFrames >= MIN_HOLD_FRAMES && !firedThisHold) {
            firedThisHold = true
            return shape.toGesture()
        }
        return null
    }

    private fun reset() {
        trail.clear()
        resetHold()
        lastTipX = Float.NaN
        lastTipY = Float.NaN
    }

    private fun resetHold() {
        heldShape = null
        heldFrames = 0
        firedThisHold = false
    }

    // ── 스와이프 ──────────────────────────────────────────────

    private fun detectSwipe(): HandGesture? {
        if (trail.size < 2) return null
        val a = trail.first()
        val b = trail.last()
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (hypot(dx, dy) < SWIPE_MIN_DISTANCE) return null
        // 화면 좌표: x 오른쪽+, y 아래+. 지배 축으로 방향 결정.
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) HandGesture.SWIPE_RIGHT else HandGesture.SWIPE_LEFT
        } else {
            if (dy < 0) HandGesture.SWIPE_UP else HandGesture.SWIPE_DOWN
        }
    }

    // ── 손 모양 ──────────────────────────────────────────────

    private fun detectShape(p: List<HandLandmarks.Point>): HandShape {
        if (p.size <= HandLandmarks.PINKY_TIP) return HandShape.UNKNOWN

        val wrist = p[HandLandmarks.WRIST]
        // 손 크기 기준 길이(정규화 좌표는 손이 프레임에서 차지하는 크기에 따라 달라진다)
        val palm = dist(wrist, p[HandLandmarks.MIDDLE_MCP]).coerceAtLeast(1e-4f)

        val thumbTip = p[HandLandmarks.THUMB_TIP]
        val indexTip = p[HandLandmarks.INDEX_TIP]

        // 핀치: 엄지·검지 끝이 붙음. 다른 판정보다 먼저(손 모양과 무관하게 우선).
        if (dist(thumbTip, indexTip) < PINCH_RATIO * palm) return HandShape.PINCH

        val indexExt = fingerExtended(p, HandLandmarks.INDEX_TIP, HandLandmarks.INDEX_PIP, wrist)
        val middleExt = fingerExtended(p, HandLandmarks.MIDDLE_TIP, HandLandmarks.MIDDLE_PIP, wrist)
        val ringExt = fingerExtended(p, HandLandmarks.RING_TIP, HandLandmarks.RING_PIP, wrist)
        val pinkyExt = fingerExtended(p, HandLandmarks.PINKY_TIP, HandLandmarks.PINKY_PIP, wrist)
        // 엄지는 접힘/폄을 손목 축이 아니라 검지 MCP와의 거리로 본다(엄지는 옆으로 벌어진다)
        val thumbExt = dist(thumbTip, p[HandLandmarks.INDEX_MCP]) > THUMB_EXT_RATIO * palm

        val fourFolded = !indexExt && !middleExt && !ringExt && !pinkyExt
        val fourExtended = indexExt && middleExt && ringExt && pinkyExt

        return when {
            thumbExt && fourFolded -> HandShape.THUMBS_UP
            fourFolded -> HandShape.FIST
            fourExtended -> HandShape.OPEN_PALM
            else -> HandShape.UNKNOWN
        }
    }

    /** 손가락 폄 판정: 끝이 PIP 관절보다 손목에서 멀면 폈다고 본다(회전에 비교적 강함). */
    private fun fingerExtended(
        p: List<HandLandmarks.Point>,
        tipIdx: Int,
        pipIdx: Int,
        wrist: HandLandmarks.Point,
    ): Boolean = dist(p[tipIdx], wrist) > dist(p[pipIdx], wrist)

    private fun dist(a: HandLandmarks.Point, b: HandLandmarks.Point): Float =
        hypot(a.x - b.x, a.y - b.y)

    private enum class HandShape {
        PINCH, FIST, OPEN_PALM, THUMBS_UP, UNKNOWN;

        fun toGesture(): HandGesture? = when (this) {
            PINCH -> HandGesture.PINCH
            FIST -> HandGesture.FIST
            OPEN_PALM -> HandGesture.OPEN_PALM
            THUMBS_UP -> HandGesture.THUMBS_UP
            UNKNOWN -> null
        }
    }

    companion object {
        /** 정적 제스처 확정에 필요한 연속 유지 프레임(SPEC §4.4, ~0.3s @15fps). */
        const val MIN_HOLD_FRAMES = 5

        /** 이 이상 프레임 간 이동하면 "움직이는 중"으로 보고 정적 유지를 리셋. */
        const val STILL_THRESHOLD = 0.03f

        /** 스와이프 인정 최소 이동(정규화, SPEC §4.4). */
        const val SWIPE_MIN_DISTANCE = 0.15f

        /** 이 시간창 안의 이동만 스와이프로 본다(SPEC §4.4). */
        const val SWIPE_MAX_MS = 500L

        /** 핀치 판정: 엄지-검지 끝 거리 / 손 크기. */
        const val PINCH_RATIO = 0.35f

        /** 엄지 폄 판정: 엄지끝-검지MCP 거리 / 손 크기. */
        const val THUMB_EXT_RATIO = 0.6f
    }
}
