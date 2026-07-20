package com.madcamp.handsfree.tracking

/**
 * [GestureClassifier]가 방출하는 이산 제스처 이벤트.
 *
 * **컨트롤러 상태를 모른다.** 어떤 명령이 될지는 [com.madcamp.handsfree.integration.GestureCommandSource]가
 * 매핑하고, 유효성은 D의 CommandGate가 판정한다(트래커가 상태를 모르는 것과 같은 원칙).
 *
 * Phase 2 범위: 안전/내비 크리티컬 명령(STOP/LOCK/UNLOCK/BACK)은 제스처에서 **제외**한다
 * (MOTION_CAPTURE_PLAN §9-C, SPEC §9-G). 잠금·정지는 음성/버튼 전담이라 손 오검출로
 * 갇히는 일이 없다.
 */
enum class HandGesture {
    /** 엄지+검지 끝이 붙음 → TOUCH */
    PINCH,

    /** 네 손가락 모두 접힘(주먹) → DRAG_START */
    FIST,

    /** 네 손가락 모두 폄(편 손바닥) → DRAG_END */
    OPEN_PALM,

    /** 엄지만 폄 → RESUME */
    THUMBS_UP,

    /** 검지 끝이 위로 빠르게 이동 → SCROLL_UP */
    SWIPE_UP,

    /** 아래로 → SCROLL_DOWN */
    SWIPE_DOWN,

    /** 사용자 왼쪽으로 → PREV */
    SWIPE_LEFT,

    /** 사용자 오른쪽으로 → NEXT */
    SWIPE_RIGHT,
}
