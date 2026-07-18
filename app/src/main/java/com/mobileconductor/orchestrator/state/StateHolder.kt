package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.ControllerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ControllerState]를 유일하게 소유하는 홀더 (명세 forD 5.6).
 *
 * 외부에는 읽기 전용 [state] Flow로만 노출한다(오버레이/디버깅용). 상태 변경은
 * Orchestrator가 [CommandGate] 판정 결과에 따라서만 [set]으로 수행한다.
 *
 * 기본 초기 상태는 CALIBRATING — 캘리브레이션 미완료 시 ACTIVE 진입을 차단한다(명세 forD 3절).
 */
class StateHolder(initial: ControllerState = ControllerState.CALIBRATING) {

    private val _state = MutableStateFlow(initial)

    /** 읽기 전용 상태 스트림. */
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    /** 현재 상태 스냅샷. */
    val current: ControllerState get() = _state.value

    /** 상태 전이. Orchestrator만 호출한다. */
    fun set(next: ControllerState) {
        _state.value = next
    }
}
