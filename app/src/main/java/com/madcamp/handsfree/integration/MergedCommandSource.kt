package com.madcamp.handsfree.integration

import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * 여러 명령원을 하나의 [VoiceCommandSource]로 합친다(음성 + 제스처).
 *
 * D의 오케스트레이터는 `voiceCommandSource` 하나만 구독하므로(포트 무변경), HAND 모드에서
 * 제스처를 추가하려면 여기서 두 스트림을 [merge]해 넘긴다. 순서 보존은 코루틴 merge의
 * 도착 순서를 따른다 — 명령은 서로 독립적이라 전역 순서가 중요하지 않다.
 *
 * MOTION_CAPTURE_PLAN §2.5 / §9-B의 "포트를 지키는" 배선 방식.
 */
class MergedCommandSource(
    private val sources: List<VoiceCommandSource>,
) : VoiceCommandSource {

    override val events: Flow<VoiceCommandEvent> =
        merge(*sources.map { it.events }.toTypedArray())
}
