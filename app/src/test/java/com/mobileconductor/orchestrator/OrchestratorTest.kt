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
    fun `LOCK in ACTIVE transitions to LOCKED without any ExecutionCommand`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.ACTIVE, scope)

        voice.inject(CommandId.LOCK)

        assertTrue("제어 명령은 C에 내려보내지 않아야 함", sink.executed.isEmpty())
        assertEquals(ControllerState.LOCKED, orch.state.value)
        scope.cancel()
    }

    @Test
    fun `invalid command in LOCKED is discarded and emits unlock rejection`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.LOCKED, scope)

        val rejections = mutableListOf<RejectReason>()
        scope.launch { orch.rejections.collect { rejections += it } }

        voice.inject(CommandId.TOUCH)

        assertTrue(sink.executed.isEmpty())
        assertEquals(ControllerState.LOCKED, orch.state.value)
        assertEquals(listOf(RejectReason.NEED_UNLOCK), rejections)
        scope.cancel()
    }

    @Test
    fun `UNLOCK in LOCKED transitions to ACTIVE without any ExecutionCommand`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val (orch, voice, sink) = harness(ControllerState.LOCKED, scope)

        voice.inject(CommandId.UNLOCK)

        assertTrue("제어 명령은 C에 내려보내지 않아야 함", sink.executed.isEmpty())
        assertEquals(ControllerState.ACTIVE, orch.state.value)
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
