package com.mobileconductor.orchestrator

import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.orchestrator.calibration.CalibrationConfig
import com.mobileconductor.orchestrator.calibration.CalibrationController
import com.mobileconductor.orchestrator.state.StateHolder
import kotlinx.coroutines.CoroutineScope

/**
 * D 모듈의 조립 지점(Composition Root).
 *
 * [ConductorDependencies]와 스코프만 주어지면 D의 객체 그래프(상태 홀더 + Orchestrator +
 * 캘리브레이션 컨트롤러)를 배선하고 Orchestrator 구독을 시작한다. Mock/실제 의존성 어느 쪽이든
 * 여기서 조립되므로, 통합 시에는 [deps]만 실제 구현으로 바꾸면 된다.
 *
 * @param deps A/B/C 경계 의존성 묶음
 * @param scope 코루틴 스코프(수명은 호출자가 관리)
 * @param initialState 시작 상태. 기본 CALIBRATING(캘리브레이션 미완료 시 ACTIVE 차단).
 * @param calibrationConfig 캘리브레이션 튜닝값
 */
class ConductorContainer(
    deps: ConductorDependencies,
    scope: CoroutineScope,
    initialState: ControllerState = ControllerState.CALIBRATING,
    calibrationConfig: CalibrationConfig = CalibrationConfig(),
) {
    private val stateHolder = StateHolder(initialState)

    val orchestrator = Orchestrator(
        stateHolder = stateHolder,
        pointerSource = deps.pointerSource,
        voiceSource = deps.voiceCommandSource,
        executionSink = deps.executionSink,
        scope = scope,
    )

    val calibrationController = CalibrationController(
        source = deps.pointerSource,
        consumer = deps.calibrationConsumer,
        config = calibrationConfig,
    )

    init {
        orchestrator.start()
    }
}
