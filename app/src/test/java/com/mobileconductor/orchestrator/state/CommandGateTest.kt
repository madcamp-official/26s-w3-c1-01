package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.CommandId.BACK
import com.mobileconductor.core.model.CommandId.DRAG_CANCEL
import com.mobileconductor.core.model.CommandId.DRAG_END
import com.mobileconductor.core.model.CommandId.DRAG_START
import com.mobileconductor.core.model.CommandId.LOCK
import com.mobileconductor.core.model.CommandId.NEXT
import com.mobileconductor.core.model.CommandId.PREV
import com.mobileconductor.core.model.CommandId.RESUME
import com.mobileconductor.core.model.CommandId.SCROLL_DOWN
import com.mobileconductor.core.model.CommandId.SCROLL_UP
import com.mobileconductor.core.model.CommandId.STOP
import com.mobileconductor.core.model.CommandId.TOUCH
import com.mobileconductor.core.model.CommandId.UNLOCK
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ControllerState.ACTIVE
import com.mobileconductor.core.model.ControllerState.CALIBRATING
import com.mobileconductor.core.model.ControllerState.DRAGGING
import com.mobileconductor.core.model.ControllerState.LOCKED
import com.mobileconductor.core.model.ControllerState.PAUSED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세 forD 8절 DoD ①② — 상태 전이표의 모든 케이스(5 상태 × 17 commandId = 85)를 전수 검증.
 *
 * [validRows]는 스펙 forD 2절 유효성 표를 main 코드([TransitionTable])와 독립적으로 다시 기술한
 * "정답지"다. 어느 한쪽이 잘못 바뀌면 이 테스트가 깨진다.
 */
class CommandGateTest {

    private val ts = 1_721_270_000_000L

    /** 유효(Accept) 조합의 정답. 여기 없는 나머지 조합은 전부 Reject여야 한다. */
    private data class ExpectedAccept(
        val state: ControllerState,
        val cmd: CommandId,
        val nextState: ControllerState,
        val emit: CommandId?,
    )

    private val validRows: List<ExpectedAccept> = listOf(
        // ACTIVE
        ExpectedAccept(ACTIVE, TOUCH, ACTIVE, TOUCH),
        ExpectedAccept(ACTIVE, BACK, ACTIVE, BACK),
        ExpectedAccept(ACTIVE, DRAG_START, DRAGGING, DRAG_START),
        ExpectedAccept(ACTIVE, SCROLL_DOWN, ACTIVE, SCROLL_DOWN),
        ExpectedAccept(ACTIVE, SCROLL_UP, ACTIVE, SCROLL_UP),
        ExpectedAccept(ACTIVE, NEXT, ACTIVE, NEXT),
        ExpectedAccept(ACTIVE, PREV, ACTIVE, PREV),
        ExpectedAccept(ACTIVE, STOP, PAUSED, null),
        ExpectedAccept(ACTIVE, LOCK, LOCKED, null),
        // PAUSED
        ExpectedAccept(PAUSED, RESUME, ACTIVE, null),
        ExpectedAccept(PAUSED, LOCK, LOCKED, null),
        // LOCKED
        ExpectedAccept(LOCKED, UNLOCK, ACTIVE, null),
        // DRAGGING
        ExpectedAccept(DRAGGING, DRAG_END, ACTIVE, DRAG_END),
        ExpectedAccept(DRAGGING, DRAG_CANCEL, ACTIVE, DRAG_CANCEL),
        ExpectedAccept(DRAGGING, STOP, PAUSED, DRAG_CANCEL), // 안전 우선
    )

    @Test
    fun `enumerates all 65 combinations`() {
        assertEquals("5 states", 5, ControllerState.values().size)
        assertEquals("13 commands", 13, CommandId.values().size)
    }

    @Test
    fun `all combinations match the spec validity table`() {
        val validMap = validRows.associateBy { it.state to it.cmd }
        var acceptCount = 0
        var rejectCount = 0

        for (state in ControllerState.values()) {
            for (cmd in CommandId.values()) {
                val decision = CommandGate.evaluate(state, cmd, ts)
                val expected = validMap[state to cmd]
                val label = "$state + $cmd"

                if (expected != null) {
                    assertTrue("$label should Accept but was $decision", decision is GateDecision.Accept)
                    decision as GateDecision.Accept
                    assertEquals("$label nextState", expected.nextState, decision.nextState)
                    assertEquals(
                        "$label emitted commandId",
                        expected.emit,
                        decision.execution?.commandId,
                    )
                    if (expected.emit != null) {
                        assertEquals("$label execution timestamp", ts, decision.execution!!.timestamp)
                    } else {
                        assertNull("$label should not emit an OS command", decision.execution)
                    }
                    acceptCount++
                } else {
                    assertTrue("$label should Reject but was $decision", decision is GateDecision.Reject)
                    rejectCount++
                }
            }
        }

        assertEquals("valid (Accept) combinations", 15, acceptCount)
        assertEquals("invalid (Reject) combinations", 65 - 15, rejectCount)
    }

    // ── 특수 케이스 (명세 forD 6절) ────────────────────────────────

    @Test
    fun `STOP during DRAGGING cancels drag first then pauses (safety priority)`() {
        val d = CommandGate.evaluate(DRAGGING, STOP, ts) as GateDecision.Accept
        assertEquals(PAUSED, d.nextState)
        assertEquals("드래그 취소를 C에 먼저 내려야 함", DRAG_CANCEL, d.execution?.commandId)
    }

    @Test
    fun `control commands transition state without emitting an OS event`() {
        for ((state, cmd) in listOf(ACTIVE to STOP, ACTIVE to LOCK, PAUSED to RESUME, LOCKED to UNLOCK)) {
            val d = CommandGate.evaluate(state, cmd, ts) as GateDecision.Accept
            assertNull("$state+$cmd must not emit ExecutionCommand", d.execution)
        }
    }

    // ── Reject 사유 ────────────────────────────────────────────────

    @Test
    fun `LOCKED rejects everything except UNLOCK with NEED_UNLOCK`() {
        for (cmd in CommandId.values()) {
            val d = CommandGate.evaluate(LOCKED, cmd, ts)
            if (cmd == UNLOCK) {
                assertTrue(d is GateDecision.Accept)
            } else {
                assertEquals("$cmd", RejectReason.NEED_UNLOCK, (d as GateDecision.Reject).reason)
            }
        }
    }

    @Test
    fun `DRAGGING rejects drag-unrelated commands with DRAG_IN_PROGRESS`() {
        val d = CommandGate.evaluate(DRAGGING, TOUCH, ts)
        assertEquals(RejectReason.DRAG_IN_PROGRESS, (d as GateDecision.Reject).reason)
    }

    @Test
    fun `CALIBRATING rejects all commands as INVALID_IN_STATE`() {
        for (cmd in CommandId.values()) {
            val d = CommandGate.evaluate(CALIBRATING, cmd, ts)
            assertEquals("$cmd", RejectReason.INVALID_IN_STATE, (d as GateDecision.Reject).reason)
        }
    }

    @Test
    fun `PAUSED rejects touch as INVALID_IN_STATE`() {
        val d = CommandGate.evaluate(PAUSED, TOUCH, ts)
        assertEquals(RejectReason.INVALID_IN_STATE, (d as GateDecision.Reject).reason)
    }
}
