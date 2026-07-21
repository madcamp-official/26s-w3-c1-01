package com.mobileconductor.orchestrator.state

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.CommandId.BACK
import com.mobileconductor.core.model.CommandId.DRAG_CANCEL
import com.mobileconductor.core.model.CommandId.DRAG_END
import com.mobileconductor.core.model.CommandId.DRAG_START
import com.mobileconductor.core.model.CommandId.LOCK
import com.mobileconductor.core.model.CommandId.NEXT
import com.mobileconductor.core.model.CommandId.PREV
import com.mobileconductor.core.model.CommandId.RESUME
import com.mobileconductor.core.model.CommandId.SCROLL_DOWN
import com.mobileconductor.core.model.CommandId.SCROLL_UP
import com.mobileconductor.core.model.CommandId.STOP
import com.mobileconductor.core.model.CommandId.TOUCH
import com.mobileconductor.core.model.CommandId.UNLOCK
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ControllerState.ACTIVE
import com.mobileconductor.core.model.ControllerState.DRAGGING
import com.mobileconductor.core.model.ControllerState.LOCKED
import com.mobileconductor.core.model.ControllerState.PAUSED

/**
 * 명세 forD 2절 유효성 표를 "데이터"로 표현한 전이 규칙 집합.
 *
 * 여기 등록된 (상태, commandId) 조합만 유효(Accept)하며, 등록되지 않은 나머지는 모두 폐기(Reject)된다.
 * CALIBRATING 상태는 어떤 음성 명령도 유효하지 않으므로 규칙이 하나도 없다(전부 Reject).
 *
 * 이 파일과 [com.mobileconductor.orchestrator.state.CommandGateTest]가 유효성 표를 이중으로 명시하여,
 * 어느 한쪽이 잘못 수정되면 테스트가 깨지도록 설계했다.
 */
object TransitionTable {

    /**
     * 유효 명령의 처리 결과.
     * @param nextState 전이할 상태
     * @param emit C에 내려보낼 OS 명령. 상태 전이만 하는 제어 명령이면 null.
     */
    data class Rule(val nextState: ControllerState, val emit: CommandId?)

    private val rules: Map<Pair<ControllerState, CommandId>, Rule> = buildMap {
        // ── ACTIVE ────────────────────────────────────────────────
        put(ACTIVE to TOUCH, Rule(ACTIVE, TOUCH))
        put(ACTIVE to BACK, Rule(ACTIVE, BACK))
        put(ACTIVE to DRAG_START, Rule(DRAGGING, DRAG_START))
        put(ACTIVE to SCROLL_DOWN, Rule(ACTIVE, SCROLL_DOWN))
        put(ACTIVE to SCROLL_UP, Rule(ACTIVE, SCROLL_UP))
        put(ACTIVE to NEXT, Rule(ACTIVE, NEXT))
        put(ACTIVE to PREV, Rule(ACTIVE, PREV))
        put(ACTIVE to STOP, Rule(PAUSED, null))       // 제어: OS 이벤트 없음
        put(ACTIVE to LOCK, Rule(LOCKED, null))        // 제어: OS 이벤트 없음

        // ── PAUSED ────────────────────────────────────────────────
        put(PAUSED to RESUME, Rule(ACTIVE, null))
        put(PAUSED to LOCK, Rule(LOCKED, null))

        // ── LOCKED ────────────────────────────────────────────────
        put(LOCKED to UNLOCK, Rule(ACTIVE, null))

        // ── DRAGGING ──────────────────────────────────────────────
        put(DRAGGING to DRAG_END, Rule(ACTIVE, DRAG_END))
        put(DRAGGING to DRAG_CANCEL, Rule(ACTIVE, DRAG_CANCEL))
        // 안전 우선: STOP 발화 시 DRAG_CANCEL을 C에 먼저 내리고 PAUSED로 전이 (명세 forD 6절)
        put(DRAGGING to STOP, Rule(PAUSED, DRAG_CANCEL))
    }

    /** 유효하면 [Rule], 무효하면 null. */
    fun rule(state: ControllerState, commandId: CommandId): Rule? = rules[state to commandId]
}
