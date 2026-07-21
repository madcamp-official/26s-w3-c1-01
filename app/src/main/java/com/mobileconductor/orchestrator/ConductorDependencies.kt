package com.mobileconductor.orchestrator

import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource

/**
 * D가 동작하는 데 필요한 외부 의존성(A/B/C 경계) 묶음.
 *
 * D는 이 인터페이스에만 의존한다. 앱에서는 A/B/C의 실제 구현을 주입하고,
 * 테스트에서는 필요한 포트만 가짜 구현으로 대체한다.
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
