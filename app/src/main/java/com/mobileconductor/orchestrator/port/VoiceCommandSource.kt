package com.mobileconductor.orchestrator.port

import com.mobileconductor.core.model.VoiceCommandEvent
import kotlinx.coroutines.flow.Flow

/**
 * B → D 경계. 정규화된 음성 명령 이벤트 스트림.
 *
 * 실제 구현은 B의 VoiceRecognizer + CommandInterpreter, 또는 Mock(디버그 버튼 주입)으로 교체된다.
 */
interface VoiceCommandSource {
    val events: Flow<VoiceCommandEvent>
}
