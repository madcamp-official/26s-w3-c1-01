package com.mobileconductor.orchestrator

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.ExecutionCommand
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.core.model.VoiceCommandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DI 조립 검증 — [ConductorContainer]가 [ConductorDependencies]로부터 객체 그래프를 배선하고
 * Orchestrator 구독을 시작해, 주입된 명령이 실제로 실행 싱크까지 흐르는지 확인한다.
 * (Mock 자리에 실제 A/B/C 구현을 넣어도 이 배선이 그대로 재사용됨을 보장하는 스모크 테스트)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConductorContainerTest {

    /** 테스트 전용 최소 의존성 묶음 — 타임스탬프 고정. */
    private class FakeDeps : ConductorDependencies {
        val events = MutableSharedFlow<VoiceCommandEvent>(extraBufferCapacity = 8)
        val executed = mutableListOf<ExecutionCommand>()

        override val pointerSource = object : PointerSource {
            override val pointerFrames = emptyFlow<PointerFrame>()
            override val rawFaceOrientation = emptyFlow<RawFaceOrientation>()
        }
        override val voiceCommandSource = object : VoiceCommandSource {
            override val events: SharedFlow<VoiceCommandEvent> = this@FakeDeps.events.asSharedFlow()
        }
        override val executionSink = object : ExecutionSink {
            private val _results = MutableSharedFlow<ExecutionResult>(extraBufferCapacity = 8)
            override val results = _results.asSharedFlow()
            override suspend fun execute(command: ExecutionCommand) { executed += command }
        }
        override val calibrationConsumer = object : CalibrationConsumer {
            override fun onProfileReady(profile: CalibrationProfile) {}
        }
    }

    @Test
    fun `container wires deps so an injected command reaches the execution sink`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val deps = FakeDeps()

        val container = ConductorContainer(
            deps = deps,
            scope = scope,
            initialState = ControllerState.ACTIVE,
        )

        deps.events.tryEmit(VoiceCommandEvent(CommandId.TOUCH, confidence = 1f, timestamp = 1000L))

        assertEquals(listOf(CommandId.TOUCH), deps.executed.map { it.commandId })
        assertEquals(ControllerState.ACTIVE, container.orchestrator.state.value)
        scope.cancel()
    }
}
