package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
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
 * B → D 경계 구현. 콜백(리스너) 기반인 B의 엔진을 D가 구독하는 Flow로 바꾼다.
 *
 * B와 D의 `VoiceCommandEvent`는 이름은 같지만 다른 타입이다 — B는 `commandId: String` +
 * rawText, D는 `commandId: CommandId` enum이다. **B의 문자열을 SSOT로 두고 여기서만
 * enum으로 바꾼다.** B의 CommandDictionary가 명령어 사전의 유일한 기준이라 B쪽을
 * enum으로 고치면 사전 수정이 두 곳으로 갈라진다.
 *
 * 두 타입을 한 파일에서 쓰므로 B쪽을 [BVoiceCommandEvent]로 별칭 처리했다.
 */
class VoiceCommandSourceAdapter(
    context: Context,
) : VoiceCommandSource, VoiceCommandListener {

    private val _events = MutableSharedFlow<VoiceCommandEvent>(
        extraBufferCapacity = 16,
        // 명령은 사람이 말하는 속도라 밀릴 일이 없지만, 밀리면 최신 명령이 더 중요하다
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<VoiceCommandEvent> = _events.asSharedFlow()

    private val engine = VoiceRecognitionEngine(context, this)

    fun start() = engine.start()

    fun stop() = engine.stop()

    /**
     * 음성을 거치지 않고 명령을 직접 넣는다.
     *
     * **LOCKED 수동 해제 버튼의 경로다(FR-005).** 음성 인식이 반복 실패하면 사용자가
     * 앱에 갇히기 때문에 물리 터치로 UNLOCK을 넣을 수단이 항상 있어야 한다.
     * UI를 정리한다고 지울 수 있는 경로가 아니다.
     */
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
        val commandId = runCatching { CommandId.valueOf(event.commandId) }.getOrNull()
        if (commandId == null) {
            // B의 사전과 D의 enum이 어긋난 경우. 조용히 삼키면 "명령이 가끔 안 먹는다"로
            // 나타나서 원인 찾기가 어렵다 — 반드시 로그를 남긴다.
            Log.e(TAG, "B의 commandId '${event.commandId}'가 D의 CommandId에 없다. 사전/enum 동기화 필요")
            return
        }
        _events.tryEmit(
            VoiceCommandEvent(
                commandId = commandId,
                confidence = event.confidence,
                timestamp = event.timestamp,
            )
        )
    }

    override fun onVoiceEngineError(error: VoiceEngineError) {
        // D에는 음성 에러 채널이 없다(OPEN_ISSUES #8). 마이크 권한 거부는 진입 화면에서
        // 이미 걸러지므로 MVP에서는 로그만 남기고, 필요해지면 포트를 추가한다.
        Log.e(TAG, "voice engine error: $error")
    }

    override fun onUnrecognizedSpeech(rawText: String) {
        Log.d(TAG, "사전에 없는 발화: $rawText")
    }

    private companion object {
        const val TAG = "VoiceAdapter"
    }
}
