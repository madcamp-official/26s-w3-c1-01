package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세 forD 8절 DoD ④ — 5개 상태 각각에 대해 오버레이 규칙(포인터 표시 방식 / 수동 해제 버튼)이
 * 명세대로 산출되는지 검증한다. 실제 Canvas 렌더는 계기 테스트(디바이스)에서 확인.
 *
 * 상단 상태 인디케이터(점+라벨)는 제거됐다 — 활성/비활성은 포인터 색, 잠금은 해제 버튼으로 구분한다.
 */
class OverlayVisualsTest {

    @Test
    fun `ACTIVE - moving pointer, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.ACTIVE)
        assertEquals(PointerVisibility.MOVING, v.pointerVisibility)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `PAUSED - fixed translucent pointer, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.PAUSED)
        assertEquals(PointerVisibility.FIXED_TRANSLUCENT, v.pointerVisibility)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `LOCKED - hidden pointer, unlock button shown`() {
        val v = OverlayVisuals.forState(ControllerState.LOCKED)
        assertEquals(PointerVisibility.HIDDEN, v.pointerVisibility)
        assertTrue("LOCKED은 수동 해제 버튼을 항상 노출", v.showManualUnlock)
    }

    @Test
    fun `DRAGGING - highlighted moving pointer, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.DRAGGING)
        assertEquals(PointerVisibility.MOVING_HIGHLIGHT, v.pointerVisibility)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `CALIBRATING - calibration-only pointer, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.CALIBRATING)
        assertEquals(PointerVisibility.CALIBRATION_ONLY, v.pointerVisibility)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `only LOCKED shows the manual unlock button`() {
        for (state in ControllerState.values()) {
            val expected = state == ControllerState.LOCKED
            assertEquals("$state", expected, OverlayVisuals.forState(state).showManualUnlock)
        }
    }
}
