package com.mobileconductor.orchestrator

import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource

/**
 * D가 동작하는 데 필요한 외부 의존성(A/B/C 경계) 묶음.
 *
 * D는 이 인터페이스에만 의존한다. 개발 중에는 Mock 구현
 * ([com.mobileconductor.mock.MockConductorDependencies])을, 통합 시에는 A/B/C의 실제 구현을
 * 담은 구현체를 [ConductorContainer]에 주입하면 나머지 배선은 그대로 재사용된다.
 *
 * - [pointerSource] : A — 포인터 프레임 / (캘리브레이션 중) 얼굴 방향
 * - [voiceCommandSource] : B — 정규화된 음성 명령
 * - [executionSink] : C — 승인 명령 실행 및 결과 반환
 * - [calibrationConsumer] : A — 완성된 캘리브레이션 프로파일 수신
 */
interface ConductorDependencies {
    val pointerSource: PointerSource
    val voiceCommandSource: VoiceCommandSource
    val executionSink: ExecutionSink
    val calibrationConsumer: CalibrationConsumer
}
