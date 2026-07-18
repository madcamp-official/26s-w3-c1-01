package com.mobileconductor

import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.mock.MockConductorDependencies
import com.mobileconductor.orchestrator.ConductorContainer
import kotlinx.coroutines.CoroutineScope

/**
 * 디버그용 조립 — Mock 의존성 묶음을 [ConductorContainer]에 주입한다.
 *
 * 통합(P6) 시에는 [MockConductorDependencies] 자리에 실제 A/B/C 구현을 담은
 * `RealConductorDependencies`를 넣기만 하면 되고, 이 파일 외의 배선은 바뀌지 않는다.
 * 디버그 편의상 초기 상태는 ACTIVE로 시작한다(캘리브레이션 플로우는 별도 진입).
 */
class DebugHarness(scope: CoroutineScope) {

    private val deps = MockConductorDependencies()
    private val container = ConductorContainer(
        deps = deps,
        scope = scope,
        initialState = ControllerState.ACTIVE,
    )

    val voice = deps.voice
    val sink = deps.sink
    val orchestrator = container.orchestrator
    val calibrationController = container.calibrationController
}
