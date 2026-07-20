package com.madcamp.handsfree.integration

import android.content.Context
import com.madcamp.handsfree.telemetry.Telemetry
import com.madcamp.handsfree.tracking.FaceTracker
import com.mobileconductor.orchestrator.ConductorDependencies
import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.ExecutionSink
import com.mobileconductor.orchestrator.port.PointerSource
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A/B/C의 실제 구현을 묶은 의존성 번들. D의 `MockConductorDependencies` 자리에 들어간다.
 *
 * D 핸드오프 §4.1이 지시한 대로 **이 클래스 하나만 갈아끼우면** ConductorContainer 이하
 * 배선은 그대로다.
 *
 * @param scope A의 좌표를 C로 중계하는 구독의 수명. Activity 스코프를 넘긴다.
 */
class RealConductorDependencies(
    context: Context,
    scope: CoroutineScope,
    val tracker: FaceTracker,
) : ConductorDependencies {

    private val telemetryLogger = Telemetry.logger(context.applicationContext)
    private val sink = InputExecutionSink(context)
    val voice = VoiceCommandSourceAdapter(context)

    override val pointerSource: PointerSource = FacePointerSource(tracker)
    override val voiceCommandSource: VoiceCommandSource = voice
    override val executionSink: ExecutionSink = sink
    override val calibrationConsumer: CalibrationConsumer = TrackerCalibrationConsumer(context, tracker)

    init {
        // A의 좌표는 두 곳으로 간다: D(오버레이 렌더/게이트)와 C(터치를 찍을 위치).
        // D를 거쳐 C로 전달되는 구조가 아니라서 여기서 직접 중계한다.
        scope.launch {
            var frameCount = 0
            var faceLostCount = 0
            var windowStartedAt = System.currentTimeMillis()

            tracker.pointerFrames.collect { frame ->
                sink.updatePointerFrame(frame)
                frameCount += 1
                if (!frame.faceDetected) faceLostCount += 1

                val now = System.currentTimeMillis()
                val elapsedMs = now - windowStartedAt
                if (elapsedMs >= POINTER_SUMMARY_INTERVAL_MS) {
                    telemetryLogger.logPerformanceSummary(
                        avgPointerFps = frameCount * 1000f / elapsedMs,
                        faceLostCount = faceLostCount,
                    )
                    frameCount = 0
                    faceLostCount = 0
                    windowStartedAt = now
                }
            }
        }
    }

    private companion object {
        const val POINTER_SUMMARY_INTERVAL_MS = 60_000L
    }
}
