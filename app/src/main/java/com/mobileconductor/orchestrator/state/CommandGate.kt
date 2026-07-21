package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ExecutionCommand

/**
 * D의 심장 — 게이트키핑 순수 함수.
 *
 * 부작용이 전혀 없다. 오직 (상태, commandId)만 보고 판정하므로 3×8=24 조합을 유닛 테스트로
 * 전수 검증할 수 있다. 상태 전이/실행은 호출자(Orchestrator)가 [GateDecision]을 보고 수행한다.
 */
object CommandGate {

    /**
     * @param state 현재 컨트롤러 상태
     * @param commandId B가 정규화해 보낸 명령
     * @param timestamp 발생 시각(epoch millis). Accept 시 [ExecutionCommand]에 실린다.
     */
    fun evaluate(state: ControllerState, commandId: CommandId, timestamp: Long): GateDecision {
        val rule = TransitionTable.rule(state, commandId)
        if (rule != null) {
            val execution = rule.emit?.let { ExecutionCommand(commandId = it, timestamp = timestamp) }
            return GateDecision.Accept(nextState = rule.nextState, execution = execution)
        }
        return GateDecision.Reject(reasonFor(state, commandId))
    }

    private fun reasonFor(state: ControllerState, commandId: CommandId): RejectReason = when {
        state == ControllerState.LOCKED && commandId != CommandId.UNLOCK ->
            RejectReason.NEED_UNLOCK

        else -> RejectReason.INVALID_IN_STATE
    }
}
