package com.mobileconductor.core.model

/**
 * D → C. 게이트키핑을 통과해 "실행 승인"된 명령.
 *
 * 제어 명령(STOP/RESUME/LOCK/UNLOCK)은 OS 이벤트가 아니므로 ExecutionCommand로
 * 내려보내지 않는다. 실제 OS 탭/드래그/스와이프가 필요한 명령만 여기로 전달된다.
 *
 * 스크롤 방향/강도, 좌표 등 실행에 필요한 부가 정보는 [payload]로 전달한다.
 * payload 키 목록은 통합 시 C와 합의한다(명세 forD 9절).
 *
 * @param commandId 실행할 명령. DRAGGING+STOP의 안전 처리 시에는 DRAG_CANCEL이 실린다.
 * @param timestamp epoch millis (예: 1721270000000)
 * @param payload 실행 파라미터(스크롤 거리 비율, 좌표 등). 기본 빈 맵.
 */
data class ExecutionCommand(
    val commandId: CommandId,
    val timestamp: Long,
    val payload: Map<String, Any> = emptyMap()
)
