package com.madcamp.handsfree.integration

import android.content.Context
import com.madcamp.handsfree.telemetry.Telemetry
import com.madcamp.handsfree.tracking.PointerTracker
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
 * ## Phase 3 — 스위처블 소스
 * [pointerSource]/[gestureSource]는 [InputModeController]가 만든 **스위처블 소스**다. 이
 * 번들은 트래커를 직접 들지 않고, 활성 트래커 접근이 필요한 곳([calibrationConsumer])은
 * [activeTracker] 프로바이더로 해결한다. 그래서 이 클래스도 오케스트레이터도 **모드 전환에
 * 따라 재생성되지 않는다** — 내부가 가리키는 트래커만 바뀐다.
 *
 * @param scope 포인터 좌표를 C로 중계하는 구독의 수명(서비스 스코프).
 * @param pointerSource 활성 트래커를 따라가는 스위처블 포인터원.
 * @param gestureSource 제스처 명령원(음성과 merge). FACE 모드에서는 랜드마크가 비어 침묵한다.
 * @param activeTracker 현재 활성 트래커 프로바이더(캘리브레이션 프로파일 주입 대상).
 * @param currentMode 현재 모드 프로바이더(프로파일을 어느 저장소에 저장할지).
 */
class RealConductorDependencies(
    context: Context,
    scope: CoroutineScope,
    override val pointerSource: PointerSource,
    gestureSource: VoiceCommandSource,
    activeTracker: () -> PointerTracker?,
    currentMode: () -> InputMode,
) : ConductorDependencies {

    private val telemetryLogger = Telemetry.logger(context.applicationContext)
    private val sink = InputExecutionSink(context)
    val voice = VoiceCommandSourceAdapter(context)

    // 음성 + 제스처를 하나의 명령 스트림으로 합친다(포트 무변경). FACE 모드에서도 gesture는
    // 붙어 있지만 랜드마크가 비어 아무 이벤트도 내지 않는다.
    override val voiceCommandSource: VoiceCommandSource = MergedCommandSource(listOf(voice, gestureSource))
    override val executionSink: ExecutionSink = sink
    override val calibrationConsumer: CalibrationConsumer =
        TrackerCalibrationConsumer(context, activeTracker, currentMode)

    init {
        // 포인터 좌표는 두 곳으로 간다: D(오버레이 렌더/게이트)와 C(터치를 찍을 위치).
        // D를 거쳐 C로 전달되는 구조가 아니라서 여기서 직접 중계한다. 스위처블 소스를
        // 구독하므로 모드를 바꿔도 자동으로 새 트래커의 좌표를 C로 보낸다.
        scope.launch {
            var frameCount = 0
            var faceLostCount = 0
            var windowStartedAt = System.currentTimeMillis()

            pointerSource.pointerFrames.collect { frame ->
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
