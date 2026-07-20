package com.mobileconductor.core.model

/**
 * B → D. 정규화된 음성 명령 이벤트.
 *
 * B가 이미 동의어 처리와 신뢰도 필터링을 마친 뒤 [CommandId]로 정규화해 전달한다.
 * D는 이 이벤트를 현재 상태에 대해 게이트키핑한다.
 *
 * @param commandId 정규화된 명령
 * @param confidence 인식 신뢰도 [0.0, 1.0] (임계값 처리는 B 책임이나 D도 참고 가능)
 * @param timestamp epoch millis
 */
data class VoiceCommandEvent(
    val commandId: CommandId,
    val confidence: Float,
    val timestamp: Long
)
