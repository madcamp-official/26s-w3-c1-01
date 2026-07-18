package com.mobileconductor.core.model

/**
 * C → D. 명령 실행 결과.
 *
 * success=true 수신 시 D는 해당 좌표에 짧은 클릭 애니메이션을 트리거한다(명세 forD 4절).
 * errorReason 코드 목록은 통합 시 C와 합의한다(명세 forD 9절).
 *
 * @param commandId 실행을 시도한 명령
 * @param success 성공 여부
 * @param x 실행 좌표(정규화 0~1). 좌표 기반이 아닌 명령이면 null
 * @param y 실행 좌표(정규화 0~1). 좌표 기반이 아닌 명령이면 null
 * @param errorReason 실패 사유 코드. 성공 시 null
 */
data class ExecutionResult(
    val commandId: CommandId,
    val success: Boolean,
    val x: Float?,
    val y: Float?,
    val errorReason: String?
)
