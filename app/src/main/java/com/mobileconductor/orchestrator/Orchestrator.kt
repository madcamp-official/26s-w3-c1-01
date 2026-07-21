package com.mobileconductor.orchestrator

import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource
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

/**
 * D의 배선 중심 — 명령 처리 파이프라인.
 *
 * B의 [VoiceCommandEvent]를 [CommandGate]로 게이트키핑하고, Accept 시 C에 [ExecutionCommand]를
 * 먼저 전달한 뒤 상태를 전이한다.
 *
 * 여러 명령이 동시에 상태를 건드리지 않도록 [mutex]로 직렬화한다.
 */
class Orchestrator(
    private val stateHolder: StateHolder,
    private val pointerSource: PointerSource,
    private val voiceSource: VoiceCommandSource,
    private val executionSink: ExecutionSink,
    private val scope: CoroutineScope,
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

    private val mutex = Mutex()

    private var job: Job? = null

    /** 음성 명령 구독을 시작한다. 중복 호출 시 무시. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            voiceSource.events.collect { handleCommand(it) }
        }
    }

    /**
     * 캘리브레이션 완료 → ACTIVE 진입. **통합 시 추가된 유일한 상태 전이 경로다.**
     *
     * [com.mobileconductor.orchestrator.state.TransitionTable]에 CALIBRATING 규칙이
     * 하나도 없어서(= 그 상태에서는 모든 음성 명령이 폐기된다) 음성으로는 여기서 빠져나올
     * 수 없다. 의도된 설계이고(FR-006: 보정 전 ACTIVE 진입 차단), 대신 보정 완료라는
     * 사실만이 전이를 일으켜야 하므로 명령이 아닌 별도 진입점으로 뒀다.
     *
     * CALIBRATING이 아닐 때 호출하면 무시한다 — 재보정 중 중복 호출로 상태가
     * 튀지 않게 하기 위해서다.
     */
    fun onCalibrationComplete() {
        scope.launch {
            mutex.withLock {
                if (stateHolder.current != ControllerState.CALIBRATING) return@withLock
                stateHolder.set(ControllerState.ACTIVE)
            }
        }
    }

    /**
     * 재보정 진입 → 상태를 CALIBRATING으로 되돌린다. [onCalibrationComplete]의 역방향.
     *
     * 상태 기계에는 ACTIVE→CALIBRATING 전이 규칙이 없다(음성 명령으로는 못 간다). 재보정은
     * 사용자가 화면에서 명시적으로 요청하는 것이라 명령이 아닌 별도 진입점으로 둔다.
     * **이게 없으면 재보정 중에도 상태가 ACTIVE라, 오버레이가 보정 UI(기준점) 대신 포인터를
     * 그려서 "그냥 시작한" 것처럼 보이고 사용자가 따라갈 점이 없어 엉터리 데이터가 수집된다.**
     */
    fun enterCalibration() {
        scope.launch {
            mutex.withLock {
                stateHolder.set(ControllerState.CALIBRATING)
            }
        }
    }

    /** 구독을 중단한다. */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun handleCommand(event: VoiceCommandEvent) = mutex.withLock {
        when (val decision = CommandGate.evaluate(stateHolder.current, event.commandId, event.timestamp)) {
            is GateDecision.Accept -> {
                decision.execution?.let { executionSink.execute(it) }
                stateHolder.set(decision.nextState)
            }

            is GateDecision.Reject -> {
                _rejections.tryEmit(decision.reason)
            }
        }
    }
}
