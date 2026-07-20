package com.mobileconductor.core.model

/**
 * A → D. 권한 거부·모델 로딩 실패 등 [PointerFrame]을 아예 만들 수 없는 상황.
 *
 * 명세서 §3 인터페이스 목록에는 없고 §4 예외 표에만 등장해서 스키마가 없었다.
 * B의 VoiceEngineError와 대칭이 되도록 A가 정한 것이다(OPEN_ISSUES #8).
 *
 * @param timestamp 단조 시계 기준 millis
 */
data class TrackerError(
    val type: Type,
    val timestamp: Long
) {
    enum class Type { CAMERA_PERMISSION_DENIED, CAMERA_UNAVAILABLE, MODEL_LOAD_FAILED }
}
