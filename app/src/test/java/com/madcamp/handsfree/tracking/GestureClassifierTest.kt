package com.madcamp.handsfree.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [GestureClassifier] 전수 검증. MediaPipe 없이 합성 랜드마크로 손 모양·스와이프·게이트를 확인한다.
 *
 * 좌표계: 손목을 화면 아래(y=1.0)에 두고 손가락이 위(y 작아짐)를 향하게 배치했다.
 * 손가락 폄 = 끝이 PIP보다 손목에서 멀다. 값은 판정이 명확하도록 과장했다.
 */
class GestureClassifierTest {

    // ── 합성 손 만들기 ────────────────────────────────────────

    /** 21점을 중립값으로 채우고 필요한 인덱스만 덮어쓴다. */
    private fun handOf(
        overrides: Map<Int, Pair<Float, Float>>,
        tipX: Float = 0.5f,
        tipY: Float = 0.5f,
        t: Long = 0L,
    ): HandLandmarks {
        val pts = MutableList(21) { HandLandmarks.Point(0.5f, 0.5f, 0f) }
        overrides.forEach { (i, xy) -> pts[i] = HandLandmarks.Point(xy.first, xy.second, 0f) }
        return HandLandmarks(pts, tipX, tipY, t)
    }

    private val base = mapOf(
        HandLandmarks.WRIST to (0.5f to 1.0f),
        HandLandmarks.MIDDLE_MCP to (0.5f to 0.6f),   // palm 기준 = 0.4
        HandLandmarks.INDEX_MCP to (0.4f to 0.65f),
    )

    /** 네 손가락 폄(끝 y≈0.1, PIP y≈0.4). */
    private val fourExtended = mapOf(
        HandLandmarks.INDEX_PIP to (0.4f to 0.4f), HandLandmarks.INDEX_TIP to (0.4f to 0.1f),
        HandLandmarks.MIDDLE_PIP to (0.5f to 0.4f), HandLandmarks.MIDDLE_TIP to (0.5f to 0.1f),
        HandLandmarks.RING_PIP to (0.6f to 0.4f), HandLandmarks.RING_TIP to (0.6f to 0.1f),
        HandLandmarks.PINKY_PIP to (0.68f to 0.42f), HandLandmarks.PINKY_TIP to (0.68f to 0.15f),
    )

    /** 네 손가락 접힘(끝이 PIP보다 손목에 가깝게 말림). */
    private val fourFolded = mapOf(
        HandLandmarks.INDEX_PIP to (0.4f to 0.5f), HandLandmarks.INDEX_TIP to (0.42f to 0.62f),
        HandLandmarks.MIDDLE_PIP to (0.5f to 0.48f), HandLandmarks.MIDDLE_TIP to (0.5f to 0.6f),
        HandLandmarks.RING_PIP to (0.6f to 0.5f), HandLandmarks.RING_TIP to (0.58f to 0.62f),
        HandLandmarks.PINKY_PIP to (0.66f to 0.52f), HandLandmarks.PINKY_TIP to (0.64f to 0.62f),
    )

    private fun openPalm(t: Long = 0L) =
        handOf(base + fourExtended + (HandLandmarks.THUMB_TIP to (0.2f to 0.6f)), t = t)

    private fun fist(t: Long = 0L) =
        handOf(base + fourFolded + (HandLandmarks.THUMB_TIP to (0.3f to 0.75f)), t = t)

    private fun thumbsUp(t: Long = 0L) =
        handOf(base + fourFolded + (HandLandmarks.THUMB_TIP to (0.25f to 0.35f)), t = t)

    private fun pinch(t: Long = 0L) =
        handOf(
            base + fourExtended +
                (HandLandmarks.INDEX_TIP to (0.4f to 0.4f)) +
                (HandLandmarks.THUMB_TIP to (0.42f to 0.42f)),
            t = t,
        )

    /** 손 미검출(빈 랜드마크). */
    private fun lost(t: Long = 0L) = HandLandmarks(emptyList(), 0.5f, 0.5f, t)

    /** 같은 프레임을 n번 먹이고 마지막 반환값을 돌려준다(정적 유지 게이트 통과 확인용). */
    private fun feed(c: GestureClassifier, frames: List<HandLandmarks>): HandGesture? {
        var last: HandGesture? = null
        frames.forEach { last = c.onFrame(it) }
        return last
    }

    // ── 정적 제스처 ──────────────────────────────────────────

    @Test
    fun `핀치를 MIN_HOLD_FRAMES 유지하면 PINCH가 확정된다`() {
        val c = GestureClassifier()
        val frames = (0 until GestureClassifier.MIN_HOLD_FRAMES).map { pinch(t = it * 66L) }
        // 마지막 프레임에서 확정
        assertEquals(HandGesture.PINCH, feed(c, frames))
    }

    @Test
    fun `유지 프레임이 모자라면 아직 확정되지 않는다`() {
        val c = GestureClassifier()
        repeat(GestureClassifier.MIN_HOLD_FRAMES - 1) { i ->
            assertNull(c.onFrame(fist(t = i * 66L)))
        }
    }

    @Test
    fun `주먹은 DRAG용 FIST로 판정된다`() {
        val c = GestureClassifier()
        val frames = (0 until GestureClassifier.MIN_HOLD_FRAMES).map { fist(t = it * 66L) }
        assertEquals(HandGesture.FIST, feed(c, frames))
    }

    @Test
    fun `엄지 척은 THUMBS_UP으로 판정된다`() {
        val c = GestureClassifier()
        val frames = (0 until GestureClassifier.MIN_HOLD_FRAMES).map { thumbsUp(t = it * 66L) }
        assertEquals(HandGesture.THUMBS_UP, feed(c, frames))
    }

    @Test
    fun `편 손바닥은 OPEN_PALM으로 판정된다`() {
        val c = GestureClassifier()
        val frames = (0 until GestureClassifier.MIN_HOLD_FRAMES).map { openPalm(t = it * 66L) }
        assertEquals(HandGesture.OPEN_PALM, feed(c, frames))
    }

    @Test
    fun `유지 중에는 한 번만 발화한다`() {
        val c = GestureClassifier()
        // 확정까지
        val fired = feed(c, (0 until GestureClassifier.MIN_HOLD_FRAMES).map { pinch(t = it * 66L) })
        assertEquals(HandGesture.PINCH, fired)
        // 계속 유지해도 재발화 없음
        repeat(5) { i ->
            assertNull(c.onFrame(pinch(t = (GestureClassifier.MIN_HOLD_FRAMES + i) * 66L)))
        }
    }

    @Test
    fun `손이 움직이는 중에는 정적 제스처가 확정되지 않는다`() {
        val c = GestureClassifier()
        // 프레임마다 STILL_THRESHOLD 이상 진동시키되(움직임) 순 이동은 스와이프 미만으로 유지
        repeat(10) { i ->
            val x = if (i % 2 == 0) 0.5f else 0.55f
            assertNull(c.onFrame(handOf(base + fourFolded + (HandLandmarks.THUMB_TIP to (0.3f to 0.75f)), tipX = x, tipY = 0.5f, t = i * 66L)))
        }
    }

    // ── 동적 제스처(스와이프) ─────────────────────────────────

    @Test
    fun `오른쪽으로 빠르게 이동하면 SWIPE_RIGHT(NEXT)`() {
        val c = GestureClassifier()
        c.onFrame(neutral(tipX = 0.3f, tipY = 0.5f, t = 0))
        assertEquals(HandGesture.SWIPE_RIGHT, c.onFrame(neutral(tipX = 0.55f, tipY = 0.5f, t = 100)))
    }

    @Test
    fun `왼쪽으로 이동하면 SWIPE_LEFT(PREV)`() {
        val c = GestureClassifier()
        c.onFrame(neutral(tipX = 0.6f, tipY = 0.5f, t = 0))
        assertEquals(HandGesture.SWIPE_LEFT, c.onFrame(neutral(tipX = 0.35f, tipY = 0.5f, t = 100)))
    }

    @Test
    fun `위로 이동하면 SWIPE_UP`() {
        val c = GestureClassifier()
        c.onFrame(neutral(tipX = 0.5f, tipY = 0.7f, t = 0))
        assertEquals(HandGesture.SWIPE_UP, c.onFrame(neutral(tipX = 0.5f, tipY = 0.45f, t = 100)))
    }

    @Test
    fun `아래로 이동하면 SWIPE_DOWN`() {
        val c = GestureClassifier()
        c.onFrame(neutral(tipX = 0.5f, tipY = 0.3f, t = 0))
        assertEquals(HandGesture.SWIPE_DOWN, c.onFrame(neutral(tipX = 0.5f, tipY = 0.55f, t = 100)))
    }

    @Test
    fun `시간창을 넘긴 느린 이동은 스와이프가 아니다`() {
        val c = GestureClassifier()
        c.onFrame(neutral(tipX = 0.3f, tipY = 0.5f, t = 0))
        // SWIPE_MAX_MS를 넘겨서 오래된 시작점이 창에서 빠지면 순 이동이 임계값 미만이 된다
        assertNull(c.onFrame(neutral(tipX = 0.55f, tipY = 0.5f, t = GestureClassifier.SWIPE_MAX_MS + 200)))
    }

    // ── 리셋 ────────────────────────────────────────────────

    @Test
    fun `손을 놓치면 유지 상태가 리셋된다`() {
        val c = GestureClassifier()
        // 확정 직전까지 쌓고
        repeat(GestureClassifier.MIN_HOLD_FRAMES - 1) { i -> c.onFrame(pinch(t = i * 66L)) }
        // 손 미검출로 리셋
        assertNull(c.onFrame(lost(t = 500)))
        // 다시 한 프레임으로는 확정되지 않아야 한다(리셋되었으므로)
        assertNull(c.onFrame(pinch(t = 600)))
    }

    /** 스와이프 방향만 볼 때 쓰는 검출된 중립 손(모양은 UNKNOWN이어도 무방). */
    private fun neutral(tipX: Float, tipY: Float, t: Long) =
        handOf(base, tipX = tipX, tipY = tipY, t = t)
}
