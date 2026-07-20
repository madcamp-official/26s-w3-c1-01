package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import com.madcamp.handsfree.telemetry.Telemetry
import com.madcamp.handsfree.voice.VoiceCommandListener
import com.madcamp.handsfree.voice.VoiceEngineError
import com.madcamp.handsfree.voice.VoiceRecognitionEngine
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.madcamp.handsfree.voice.VoiceCommandEvent as BVoiceCommandEvent

/**
 * B -> D boundary. Converts B's callback-based voice engine into the Flow consumed by D.
 */
class VoiceCommandSourceAdapter(
    context: Context,
) : VoiceCommandSource, VoiceCommandListener {

    private val _events = MutableSharedFlow<VoiceCommandEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<VoiceCommandEvent> = _events.asSharedFlow()

    private val telemetryLogger = Telemetry.logger(context.applicationContext)
    private val engine = VoiceRecognitionEngine(context, this)
    private var voiceSuccessCount = 0
    private var voiceFailureCount = 0
    private var voiceWindowStartedAt = System.currentTimeMillis()

    fun start() = engine.start()

    fun stop() = engine.stop()

    fun inject(commandId: CommandId) {
        _events.tryEmit(
            VoiceCommandEvent(
                commandId = commandId,
                confidence = 1f,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    override fun onVoiceCommand(event: BVoiceCommandEvent) {
        Log.i(TAG, "발화 인식 - \"${event.rawText}\" -> ${event.commandId} (신뢰도 ${event.confidence})")

        val commandId = runCatching { CommandId.valueOf(event.commandId) }.getOrNull()
        if (commandId == null) {
            Log.e(TAG, "B의 commandId '${event.commandId}'가 D의 CommandId에 없다. 사전/enum 동기화 필요")
            recordVoiceFailure("UNKNOWN_COMMAND_ID")
            return
        }

        recordVoiceSuccess()
        _events.tryEmit(
            VoiceCommandEvent(
                commandId = commandId,
                confidence = event.confidence,
                timestamp = event.timestamp,
            )
        )
    }

    override fun onVoiceEngineError(error: VoiceEngineError) {
        Log.e(TAG, "voice engine error: $error")
        recordVoiceFailure(error::class.simpleName ?: "VOICE_ENGINE_ERROR")
    }

    override fun onUnrecognizedSpeech(rawText: String) {
        // **인식된 문장을 그대로 찍지 않는다.** 마이크가 상시 켜져 있는 앱이라
        // 여기 들어오는 건 명령어가 아닌 모든 소리다 — 사용자의 혼잣말, 옆 사람 대화,
        // TV 소리까지 전부. 그걸 로그에 남기면 "음성 원본은 저장하지 않는다"는 NFR과
        // 앱이 사용자에게 보여주는 고지("수집하지 않음: 음성 녹음")를 동시에 깬다.
        // 길이만으로도 "짧게 끊겼는지 / 길게 말했는데 못 알아들었는지"는 구분된다.
        Log.i(TAG, "발화는 들렸으나 사전에 없음 (${rawText.length}자)")
        recordVoiceFailure("UNRECOGNIZED_SPEECH")
    }

    private fun recordVoiceSuccess() {
        voiceSuccessCount += 1
        publishVoiceSummaryIfNeeded()
    }

    private fun recordVoiceFailure(reason: String) {
        voiceFailureCount += 1
        telemetryLogger.logVoiceRecognitionFailed(reason)
        publishVoiceSummaryIfNeeded()
    }

    private fun publishVoiceSummaryIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - voiceWindowStartedAt < VOICE_SUMMARY_INTERVAL_MS) return

        val total = voiceSuccessCount + voiceFailureCount
        val failureRate = if (total == 0) 0f else voiceFailureCount.toFloat() / total
        telemetryLogger.logPerformanceSummary(voiceFailureRate = failureRate)
        voiceSuccessCount = 0
        voiceFailureCount = 0
        voiceWindowStartedAt = now
    }

    private companion object {
        const val TAG = "HF-VoiceCmd"
        const val VOICE_SUMMARY_INTERVAL_MS = 60_000L
    }
}
