package com.mobileconductor.mock

import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * B 대체 Mock (명세 forD 7절).
 *
 * 디버그 UI의 버튼/키 입력으로 임의 [CommandId]를 주입한다. B가 정규화까지 마친 상태를
 * 흉내 내므로, D는 이 Mock과 실제 B를 구분하지 못한다.
 *
 * @param clock 타임스탬프 소스(테스트 주입용)
 */
class MockVoiceCommandSource(
    private val clock: () -> Long = System::currentTimeMillis,
) : VoiceCommandSource {

    private val _events = MutableSharedFlow<VoiceCommandEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<VoiceCommandEvent> = _events.asSharedFlow()

    /** 주어진 명령을 즉시 주입한다. 구독자가 없으면 드롭된다(hot stream). */
    fun inject(commandId: CommandId, confidence: Float = 1.0f) {
        _events.tryEmit(VoiceCommandEvent(commandId, confidence, clock()))
    }
}
