package com.madcamp.handsfree.voice

import org.json.JSONObject

/**
 * B → D로 전달되는 유일한 출력. C에게는 직접 전달하지 않는다(D가 유효성 검증 후 ExecutionCommand로 내려줌).
 */
data class VoiceCommandEvent(
    val commandId: String,
    val rawText: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): String = JSONObject()
        .put("commandId", commandId)
        .put("rawText", rawText)
        .put("confidence", confidence.toDouble())
        .put("timestamp", timestamp)
        .toString()
}

/** B가 스스로 처리할 수 없는 상황(마이크 권한 거부 등)을 D에게 알리기 위한 에러 채널. */
sealed class VoiceEngineError {
    data class MicPermissionError(val message: String = "RECORD_AUDIO permission denied") : VoiceEngineError()
    data class RecognizerUnavailable(val message: String = "Speech recognition not available on this device") : VoiceEngineError()
}

interface VoiceCommandListener {
    /** 사전 매칭 + 신뢰도 임계값을 통과한 명령. */
    fun onVoiceCommand(event: VoiceCommandEvent)

    /** 마이크 권한 거부 등 B가 복구할 수 없는 상황. */
    fun onVoiceEngineError(error: VoiceEngineError)

    /** 사전에 없는 발화 디버그 로그(선택 구현, 기본은 무시). */
    fun onUnrecognizedSpeech(rawText: String) {}
}
