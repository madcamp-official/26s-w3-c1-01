package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ExecutionCommand

/**
 * 게이트키핑 판정 결과.
 *
 * - [Accept]: 현재 상태에서 유효한 명령. 상태를 [nextState]로 전이하고,
 *   OS 이벤트가 필요한 경우 [execution]을 C에 내려보낸다.
 *   제어 명령(LOCK/UNLOCK)은 상태만 바꾸므로 execution == null.
 * - [Reject]: 무효 명령. 폐기하고 [reason]에 따라 안내만 표시한다.
 */
sealed interface GateDecision {
    data class Accept(
        val nextState: ControllerState,
        val execution: ExecutionCommand?
    ) : GateDecision

    data class Reject(val reason: RejectReason) : GateDecision
}

/** 명령이 폐기된 사유. 오버레이 안내 문구 매핑에 사용. */
enum class RejectReason {
    /** 현재 상태에서 애초에 유효하지 않은 명령. */
    INVALID_IN_STATE,

    /** LOCKED 상태 — "잠금 해제가 필요합니다" (명세 forD 6절). */
    NEED_UNLOCK
}
