package com.madcamp.handsfree.integration

import android.content.Context
import com.example.hands_free_controller.input.InputExecutionEngine
import com.madcamp.handsfree.telemetry.Telemetry
import com.mobileconductor.core.model.ExecutionCommand
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.orchestrator.port.ExecutionSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * D → C 경계 구현. 콜백 기반인 C의 실행 엔진을 D의 [ExecutionSink]에 맞춘다.
 *
 * C의 엔진은 자기가 마지막으로 받은 [PointerFrame] 위치에 터치를 찍는다. 즉 A의
 * 좌표 스트림을 D와 **별도로** C에도 흘려줘야 한다 — [updatePointerFrame]이 그 통로고,
 * 배선은 [RealConductorDependencies]가 한다. 이걸 빠뜨리면 명령은 전달되는데
 * 좌표가 없어서 전부 `NO_POINTER`로 실패한다.
 */
class InputExecutionSink(
    context: Context,
) : ExecutionSink {

    private val engine = InputExecutionEngine(context)
    private val telemetryLogger = Telemetry.logger(context.applicationContext)

    private val _results = MutableSharedFlow<ExecutionResult>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val results: Flow<ExecutionResult> = _results.asSharedFlow()

    fun updatePointerFrame(frame: PointerFrame) = engine.updatePointerFrame(frame)

    override suspend fun execute(command: ExecutionCommand) {
        // dispatchGesture는 접근성 서비스의 메인 루퍼에서 호출하는 게 안전하다.
        // 드래그의 continueStroke는 앞선 stroke와 같은 스레드 순서를 전제로 이어진다.
        withContext(Dispatchers.Main) {
            engine.execute(command) { result ->
                telemetryLogger.logCommandExecuted(
                    result = result,
                    voiceToExecutionMs = System.currentTimeMillis() - command.timestamp,
                )
                _results.tryEmit(result)
            }
        }
    }
}
