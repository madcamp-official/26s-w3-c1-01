package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateHolderTest {

    @Test
    fun `defaults to CALIBRATING to block ACTIVE before calibration`() {
        assertEquals(ControllerState.CALIBRATING, StateHolder().current)
    }

    @Test
    fun `set updates current and emits latest to state flow`() {
        val holder = StateHolder(ControllerState.ACTIVE)
        holder.set(ControllerState.LOCKED)
        assertEquals(ControllerState.LOCKED, holder.current)
        assertEquals(ControllerState.LOCKED, holder.state.value)
    }
}
