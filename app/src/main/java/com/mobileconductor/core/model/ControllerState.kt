package com.mobileconductor.core.model

/**
 * 컨트롤러 전역 상태 (명세 forD 2절 / 5.6).
 *
 * D(Orchestrator)만이 이 상태를 소유·전이시킨다. A/B/C는 이 상태를 몰라도
 * 동작하도록 설계되어 있으며, 외부에는 디버깅/오버레이 목적의 읽기 전용으로만 노출한다.
 */
enum class ControllerState {
    /** 초기 보정 진행 중. ACTIVE 진입 차단. */
    CALIBRATING,

    /** 모든 유효 명령 처리. */
    ACTIVE,

    /** 포인터 고정. RESUME만 유효. */
    PAUSED,

    /** 모든 입력 무시. UNLOCK만 유효. */
    LOCKED,

    /** 드래그 진행 중. DRAG_END / DRAG_CANCEL / STOP(안전 우선)만 유효. */
    DRAGGING
}
