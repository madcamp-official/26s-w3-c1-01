package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.CommandId.BACK
import com.mobileconductor.core.model.CommandId.HOME
import com.mobileconductor.core.model.CommandId.LOCK
import com.mobileconductor.core.model.CommandId.NEXT
import com.mobileconductor.core.model.CommandId.PREV
import com.mobileconductor.core.model.CommandId.SCROLL_DOWN
import com.mobileconductor.core.model.CommandId.SCROLL_UP
import com.mobileconductor.core.model.CommandId.TOUCH
import com.mobileconductor.core.model.CommandId.UNLOCK
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ControllerState.ACTIVE
import com.mobileconductor.core.model.ControllerState.CALIBRATING
import com.mobileconductor.core.model.ControllerState.LOCKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 상태 전이표의 모든 케이스(3 상태 × 9 commandId = 27)를 전수 검증.
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
        ExpectedAccept(ACTIVE, SCROLL_DOWN, ACTIVE, SCROLL_DOWN),
        ExpectedAccept(ACTIVE, SCROLL_UP, ACTIVE, SCROLL_UP),
        ExpectedAccept(ACTIVE, NEXT, ACTIVE, NEXT),
        ExpectedAccept(ACTIVE, PREV, ACTIVE, PREV),
        ExpectedAccept(ACTIVE, HOME, ACTIVE, HOME),    // 홈으로 — 다른 명령처럼 ACTIVE에서만
        ExpectedAccept(ACTIVE, LOCK, LOCKED, null),
        // LOCKED
        ExpectedAccept(LOCKED, UNLOCK, ACTIVE, null),
    )

    @Test
    fun `enumerates all 27 combinations`() {
        assertEquals("3 states", 3, ControllerState.values().size)
        assertEquals("9 commands", 9, CommandId.values().size)
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

        assertEquals("valid (Accept) combinations", 9, acceptCount)
        assertEquals("invalid (Reject) combinations", 27 - 9, rejectCount)
    }

    @Test
    fun `control commands transition state without emitting an OS event`() {
        for ((state, cmd) in listOf(ACTIVE to LOCK, LOCKED to UNLOCK)) {
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
    fun `CALIBRATING rejects all commands as INVALID_IN_STATE`() {
        for (cmd in CommandId.values()) {
            val d = CommandGate.evaluate(CALIBRATING, cmd, ts)
            assertEquals("$cmd", RejectReason.INVALID_IN_STATE, (d as GateDecision.Reject).reason)
        }
    }

}
