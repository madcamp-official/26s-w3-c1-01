package com.mobileconductor.mock

import com.mobileconductor.core.model.ExecutionCommand
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.orchestrator.port.ExecutionSink
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * C 대체 Mock (명세 forD 7절). 항상 success:true.
 *
 * 전달받은 명령을 [executed]에 기록(테스트 검증용)하고, 즉시 성공 [ExecutionResult]를 되돌린다.
 */
class MockExecutionSink : ExecutionSink {

    private val _results = MutableSharedFlow<ExecutionResult>(extraBufferCapacity = 32)
    override val results: SharedFlow<ExecutionResult> = _results.asSharedFlow()

    /** C에 전달된 명령 이력(테스트/디버그 확인용). */
    val executed: List<ExecutionCommand> get() = _executed
    private val _executed = mutableListOf<ExecutionCommand>()

    override suspend fun execute(command: ExecutionCommand) {
        _executed += command
        _results.tryEmit(
            ExecutionResult(
                commandId = command.commandId,
                success = true,
                x = null,
                y = null,
                errorReason = null,
            )
        )
    }
}
