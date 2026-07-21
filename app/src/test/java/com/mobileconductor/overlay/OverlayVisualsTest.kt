package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세 forD 8절 DoD ④ — 5개 상태 각각에 대해 오버레이 시각 규칙(포인터 표시 방식/인디케이터/라벨/
 * 해제버튼)이 명세 4절 표대로 산출되는지 검증한다. 실제 Canvas 렌더는 계기 테스트(디바이스)에서 확인.
 */
class OverlayVisualsTest {

    @Test
    fun `ACTIVE - moving pointer, green, no label, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.ACTIVE)
        assertEquals(PointerVisibility.MOVING, v.pointerVisibility)
        assertEquals(IndicatorColor.GREEN, v.indicatorColor)
        assertNull(v.label)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `PAUSED - fixed translucent, yellow, paused label`() {
        val v = OverlayVisuals.forState(ControllerState.PAUSED)
        assertEquals(PointerVisibility.FIXED_TRANSLUCENT, v.pointerVisibility)
        assertEquals(IndicatorColor.YELLOW, v.indicatorColor)
        assertEquals("일시정지", v.label)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `LOCKED - hidden pointer, gray, locked label, unlock button shown`() {
        val v = OverlayVisuals.forState(ControllerState.LOCKED)
        assertEquals(PointerVisibility.HIDDEN, v.pointerVisibility)
        assertEquals(IndicatorColor.GRAY, v.indicatorColor)
        assertEquals("잠김", v.label)
        assertTrue("LOCKED은 수동 해제 버튼을 항상 노출", v.showManualUnlock)
    }

    @Test
    fun `DRAGGING - highlighted moving pointer, blue, dragging label`() {
        val v = OverlayVisuals.forState(ControllerState.DRAGGING)
        assertEquals(PointerVisibility.MOVING_HIGHLIGHT, v.pointerVisibility)
        assertEquals(IndicatorColor.BLUE, v.indicatorColor)
        assertEquals("드래그 중", v.label)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `CALIBRATING - calibration-only UI with progress indicator`() {
        val v = OverlayVisuals.forState(ControllerState.CALIBRATING)
        assertEquals(PointerVisibility.CALIBRATION_ONLY, v.pointerVisibility)
        assertEquals(IndicatorColor.PROGRESS, v.indicatorColor)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `only LOCKED shows the manual unlock button`() {
        for (state in ControllerState.values()) {
            val expected = state == ControllerState.LOCKED
            assertEquals("$state", expected, OverlayVisuals.forState(state).showManualUnlock)
        }
    }

    @Test
    fun `only LOCKED hides the status hud`() {
        // 잠금은 화면을 비우려고 상단 인디케이터/라벨을 끈다. 나머지 상태는 모두 표시한다.
        for (state in ControllerState.values()) {
            val expected = state != ControllerState.LOCKED
            assertEquals("$state", expected, OverlayVisuals.forState(state).showStatusHud)
        }
    }

    @Test
    fun `LOCKED hides status hud but keeps the unlock button`() {
        val v = OverlayVisuals.forState(ControllerState.LOCKED)
        assertFalse("잠금 시 상태 HUD는 숨긴다", v.showStatusHud)
        assertTrue("그래도 수동 해제 버튼은 남긴다", v.showManualUnlock)
    }
}
