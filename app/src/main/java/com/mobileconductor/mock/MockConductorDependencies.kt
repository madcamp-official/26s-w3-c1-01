package com.mobileconductor.mock

import com.mobileconductor.orchestrator.ConductorDependencies
import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource

/**
 * A/B/C 전체를 Mock으로 채운 의존성 묶음 (명세 forD 7절).
 *
 * 통합 시 이 클래스를 실제 A/B/C 구현을 담은 `RealConductorDependencies`로 교체하면
 * [com.mobileconductor.orchestrator.ConductorContainer]의 배선은 그대로 재사용된다.
 *
 * 디버그 화면에서 명령 주입/실행 이력 확인이 필요하므로 구체 Mock 타입([voice], [sink])도
 * 함께 노출한다.
 */
class MockConductorDependencies : ConductorDependencies {

    val voice = MockVoiceCommandSource()
    val sink = MockExecutionSink()
    val calibration = MockCalibrationConsumer()

    override val pointerSource: PointerSource = MockPointerSource()
    override val voiceCommandSource: VoiceCommandSource = voice
    override val executionSink: ExecutionSink = sink
    override val calibrationConsumer: CalibrationConsumer = calibration
}
