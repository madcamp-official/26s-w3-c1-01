package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오버레이 규칙(포인터 표시 방식 / 수동 해제 버튼)이 현재 상태 정책대로 산출되는지 검증한다.
 * 상단 상태 인디케이터는 제거됐고, 활성/비활성은 포인터 색과 잠금 해제 버튼으로 구분한다.
 */
class OverlayVisualsTest {

    @Test
    fun `ACTIVE - moving pointer, no unlock`() {
        val v = OverlayVisuals.forState(ControllerState.ACTIVE)
        assertEquals(PointerVisibility.MOVING, v.pointerVisibility)
        assertFalse(v.showManualUnlock)
    }

    @Test
    fun `LOCKED - hidden pointer, unlock button shown`() {
        val v = OverlayVisuals.forState(ControllerState.LOCKED)
        assertEquals(PointerVisibility.HIDDEN, v.pointerVisibility)
        assertTrue("LOCKED은 수동 해제 버튼을 항상 노출", v.showManualUnlock)
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
