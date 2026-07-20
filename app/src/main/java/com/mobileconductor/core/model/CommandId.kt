package com.mobileconductor.core.model

/**
 * 정규화된 명령 식별자 (명세 forD 2절 유효성 표 기준, 총 17종).
 *
 * B(Voice)가 발화 → 동의어 처리 → commandId로 정규화한 뒤 D에 전달한다.
 * D는 오직 이 enum 값만 보고 게이트키핑을 수행한다(원문 텍스트/동의어를 모른다).
 *
 * 상태 × commandId = 5 × 17 = 85 조합이 [com.mobileconductor.orchestrator.state.CommandGate]의
 * 전수 유닛 테스트 대상이다.
 */
enum class CommandId {
    // 터치 — FR-002
    TOUCH,
    BACK,               // 취소(뒤로가기)

    // 드래그 — FR-003
    DRAG_START,
    DRAG_END,
    DRAG_CANCEL,

    // 스크롤 — FR-004 (방향 × 강도)
    SCROLL_DOWN,        // 기본 아래 (~50%)
    SCROLL_UP,          // 기본 위   (~50%)
    SCROLL_DOWN_SMALL,  // 조금 아래 (~20%)
    SCROLL_UP_SMALL,    // 조금 위   (~20%)
    SCROLL_DOWN_LARGE,  // 크게 아래 (~80%)
    SCROLL_UP_LARGE,    // 크게 위   (~80%)

    // 발표(시나리오4) — 좌/우 스와이프로 매핑
    NEXT,
    PREV,

    // 제어 — FR-005 (OS 이벤트 아님, 상태 전이 전용)
    STOP,               // 멈춰 → PAUSED
    RESUME,             // 다시 시작 → ACTIVE
    LOCK,               // 잠금 → LOCKED
    UNLOCK              // 해제 → ACTIVE
}
