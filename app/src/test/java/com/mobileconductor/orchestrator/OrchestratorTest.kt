package com.mobileconductor.orchestrator

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.mock.MockExecutionSink
import com.mobileconductor.mock.MockPointerSource
import com.mobileconductor.mock.MockVoiceCommandSource
import com.mobileconductor.orchestrator.state.RejectReason
import com.mobileconductor.orchestrator.state.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세 forD 8절 DoD ② — 더미 VoiceCommandEvent 주입 시 상태별 유효성 표대로
 * ExecutionCommand가 생성/폐기되는지 파이프라인 수준에서 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorTest {

    private fun harness(
        initial: ControllerState,
        scope: CoroutineScope,
    ): Triple<Orchestrator, MockVoiceCommandSource, MockExecutionSink> {
        val voice = MockVoiceCommandSource { 1000L }
        val sink = MockExecutionSink()
        val orch = Orchestrator(
            stateHolder = StateHolder(initial),
            pointerSource = MockPointerSource(clock = { 1000L }),
            voiceSource = voice,
            executionSink = sink,
            scope = scope,
        )
        orch.start()
        return Triple(orch, voice, sink)
    }

    @Test
    fun `TOUCH in ACTIVE forwards ExecutionCommand and stays ACTIVE`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        voice.inject(CommandId.TOUCH)

        assertEquals(1, sink.executed.size)
        assertEquals(CommandId.TOUCH, sink.executed.first().commandId)
        assertEquals(ControllerState.ACTIVE, orch.state.value)
        scope.cancel()
    }

    @Test
    fun `STOP in ACTIVE transitions to PAUSED without any ExecutionCommand`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        voice.inject(CommandId.STOP)

        assertTrue("제어 명령은 C에 내려보내지 않아야 함", sink.executed.isEmpty())
        assertEquals(ControllerState.PAUSED, orch.state.value)
        scope.cancel()
    }

    @Test
    fun `invalid command in PAUSED is discarded and emits rejection`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.PAUSED, scope)

        val rejections = mutableListOf<RejectReason>()
        scope.launch { orch.rejections.collect { rejections += it } }

        voice.inject(CommandId.TOUCH)

        assertTrue(sink.executed.isEmpty())
        assertEquals(ControllerState.PAUSED, orch.state.value)
        assertEquals(listOf(RejectReason.INVALID_IN_STATE), rejections)
        scope.cancel()
    }

    @Test
    fun `STOP during DRAGGING cancels drag first then pauses (safety priority)`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.DRAGGING, scope)

        voice.inject(CommandId.STOP)

        // DRAG_CANCEL을 C에 먼저 내려보낸 뒤 PAUSED로 전이
        assertEquals(1, sink.executed.size)
        assertEquals(CommandId.DRAG_CANCEL, sink.executed.first().commandId)
        assertEquals(ControllerState.PAUSED, orch.state.value)
        scope.cancel()
    }

    @Test
    fun `full drag lifecycle - start then end`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        voice.inject(CommandId.DRAG_START)
        assertEquals(ControllerState.DRAGGING, orch.state.value)

        voice.inject(CommandId.DRAG_END)
        assertEquals(ControllerState.ACTIVE, orch.state.value)

        assertEquals(
            listOf(CommandId.DRAG_START, CommandId.DRAG_END),
            sink.executed.map { it.commandId },
        )
        scope.cancel()
    }

    @Test
    fun `DRAGGING auto-cancels after 30s and returns to ACTIVE with a notice`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        val notices = mutableListOf<OrchestratorNotice>()
        scope.launch { orch.notices.collect { notices += it } }

        voice.inject(CommandId.DRAG_START)
        assertEquals(ControllerState.DRAGGING, orch.state.value)

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals("타임아웃 전에는 DRAGGING 유지", ControllerState.DRAGGING, orch.state.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(ControllerState.ACTIVE, orch.state.value)
        assertEquals(
            listOf(CommandId.DRAG_START, CommandId.DRAG_CANCEL),
            sink.executed.map { it.commandId },
        )
        assertEquals(listOf(OrchestratorNotice.DRAG_AUTO_CANCELLED), notices)
        scope.cancel()
    }

    @Test
    fun `DRAG_END before timeout prevents auto-cancel`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        voice.inject(CommandId.DRAG_START)
        advanceTimeBy(10_000)
        runCurrent()
        voice.inject(CommandId.DRAG_END)

        advanceTimeBy(60_000) // 타이머가 해제됐어야 함
        runCurrent()

        assertEquals(ControllerState.ACTIVE, orch.state.value)
        assertEquals(
            "자동 취소가 일어나지 않아야 함",
            listOf(CommandId.DRAG_START, CommandId.DRAG_END),
            sink.executed.map { it.commandId },
        )
        scope.cancel()
    }

    @Test
    fun `successful execution produces a result for overlay feedback`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, _) = harness(ControllerState.ACTIVE, scope)

        val results = mutableListOf<CommandId>()
        scope.launch { orch.executionResults.collect { results += it.commandId } }

        voice.inject(CommandId.TOUCH)

        assertEquals(listOf(CommandId.TOUCH), results)
        scope.cancel()
    }
}
