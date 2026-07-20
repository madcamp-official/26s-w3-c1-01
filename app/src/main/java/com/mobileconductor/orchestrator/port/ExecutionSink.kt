package com.mobileconductor.orchestrator.port

import com.mobileconductor.core.model.ExecutionCommand
import com.mobileconductor.core.model.ExecutionResult
import kotlinx.coroutines.flow.Flow

/**
 * D → C 경계. 승인된 [ExecutionCommand]를 C에 전달하고, C의 [ExecutionResult]를 받는다.
 *
 * 실제 구현은 C의 InputInjector(접근성 서비스), 또는 Mock(always success)으로 교체된다.
 */
interface ExecutionSink {
    /** 게이트키핑을 통과한 명령을 C에 실행 요청한다. */
    suspend fun execute(command: ExecutionCommand)

    /** C가 돌려주는 실행 결과 스트림. success=true 시 D가 클릭 애니메이션을 트리거. */
    val results: Flow<ExecutionResult>
}
