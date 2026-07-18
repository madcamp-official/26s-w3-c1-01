package com.mobileconductor.orchestrator

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import com.mobileconductor.orchestrator.safety.DragWatchdog
import com.mobileconductor.orchestrator.state.CommandGate
import com.mobileconductor.orchestrator.state.GateDecision
import com.mobileconductor.orchestrator.state.RejectReason
import com.mobileconductor.orchestrator.state.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 사용자에게 알려야 하는 오케스트레이터 이벤트(안내 메시지용). */
enum class OrchestratorNotice {
    /** 드래그가 장시간 방치되어 자동 취소됨(명세 forD 6절). */
    DRAG_AUTO_CANCELLED,
}

/**
 * D의 배선 중심 — 명령 처리 파이프라인 + 안전장치 (명세 forD 1절/6절).
 *
 * B의 [VoiceCommandEvent]를 [CommandGate]로 게이트키핑하고, Accept 시 C에 [ExecutionCommand]를
 * 먼저 전달한 뒤 상태를 전이한다. DRAGGING 진입 시 [DragWatchdog]로 30초 자동 취소 타이머를 건다.
 *
 * 명령 처리와 자동 취소가 동시에 상태를 건드리지 않도록 [mutex]로 직렬화한다.
 */
class Orchestrator(
    private val stateHolder: StateHolder,
    private val pointerSource: PointerSource,
    private val voiceSource: VoiceCommandSource,
    private val executionSink: ExecutionSink,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    dragTimeoutMs: Long = DragWatchdog.DEFAULT_TIMEOUT_MS,
) {
    /** 읽기 전용 상태 스트림(오버레이/디버깅용). */
    val state: StateFlow<ControllerState> = stateHolder.state

    /** A의 포인터 프레임(오버레이 렌더용). */
    val pointerFrames: Flow<PointerFrame> = pointerSource.pointerFrames

    /** C의 실행 결과(오버레이 클릭 애니메이션용). */
    val executionResults: Flow<ExecutionResult> = executionSink.results

    private val _rejections = MutableSharedFlow<RejectReason>(extraBufferCapacity = 16)

    /** 폐기된 명령의 사유(오버레이 안내 문구용). */
    val rejections: SharedFlow<RejectReason> = _rejections.asSharedFlow()

    private val _notices = MutableSharedFlow<OrchestratorNotice>(extraBufferCapacity = 8)

    /** 안내 이벤트(드래그 자동 취소 등). */
    val notices: SharedFlow<OrchestratorNotice> = _notices.asSharedFlow()

    private val mutex = Mutex()
    private val watchdog = DragWatchdog(scope, dragTimeoutMs) { autoCancelDrag() }

    private var job: Job? = null

    /** 음성 명령 구독을 시작한다. 중복 호출 시 무시. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            voiceSource.events.collect { handleCommand(it) }
        }
    }

    /** 구독을 중단하고 안전장치 타이머를 해제한다. */
    fun stop() {
        job?.cancel()
        job = null
        watchdog.cancel()
    }

    private suspend fun handleCommand(event: VoiceCommandEvent) = mutex.withLock {
        when (val decision = CommandGate.evaluate(stateHolder.current, event.commandId, event.timestamp)) {
            is GateDecision.Accept -> {
                // 안전 우선(DRAGGING+STOP)의 DRAG_CANCEL 포함 — C에 "먼저" 내려보낸 뒤 전이
                decision.execution?.let { executionSink.execute(it) }
                stateHolder.set(decision.nextState)
                onStateEntered(decision.nextState)
            }

            is GateDecision.Reject -> {
                _rejections.tryEmit(decision.reason)
            }
        }
    }

    /** DRAGGING 진입 시 자동 취소 타이머 시작, 이탈 시 해제. */
    private fun onStateEntered(state: ControllerState) {
        if (state == ControllerState.DRAGGING) watchdog.start() else watchdog.cancel()
    }

    /** 30초 방치 시 호출 — 아직 DRAGGING이면 DRAG_CANCEL을 C에 내리고 ACTIVE로 복귀 + 안내. */
    private suspend fun autoCancelDrag() = mutex.withLock {
        if (stateHolder.current != ControllerState.DRAGGING) return@withLock
        val decision = CommandGate.evaluate(ControllerState.DRAGGING, CommandId.DRAG_CANCEL, clock())
        if (decision is GateDecision.Accept) {
            decision.execution?.let { executionSink.execute(it) }
            stateHolder.set(decision.nextState)
            onStateEntered(decision.nextState)
        }
        _notices.tryEmit(OrchestratorNotice.DRAG_AUTO_CANCELLED)
    }
}
