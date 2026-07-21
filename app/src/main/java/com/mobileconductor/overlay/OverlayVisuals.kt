package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState

/** 포인터 표시 방식 (명세 forD 4절). */
enum class PointerVisibility {
    /** 실시간 이동, 기본 색상 (ACTIVE). */
    MOVING,

    /** 마지막 위치 고정, 반투명 (PAUSED). */
    FIXED_TRANSLUCENT,

    /** 숨김/매우 반투명 (LOCKED). */
    HIDDEN,

    /** 이동 중, 테두리 강조 (DRAGGING). */
    MOVING_HIGHLIGHT,

    /** 포인터 대신 기준점 전용 UI (CALIBRATING). */
    CALIBRATION_ONLY,
}

/** 상태 인디케이터 색상 (명세 forD 4절). 실제 ARGB는 렌더 레이어에서 매핑. */
enum class IndicatorColor { GREEN, YELLOW, GRAY, BLUE, PROGRESS }

/**
 * 상태 → 오버레이 시각 규칙 매핑 결과. 순수 데이터라 상태별 규칙을 유닛 테스트할 수 있다(DoD 4).
 *
 * 상단 상태 인디케이터(점+라벨)는 제거했다 — 활성은 포인터(흰 링), 비활성은 회색 링,
 * 잠금은 "잠금 해제" 버튼으로 구분되므로 별도 표시가 필요 없어졌다.
 *
 * @param pointerVisibility 포인터 표시 방식
 * @param showManualUnlock LOCKED에서 수동 잠금 해제 버튼 노출 여부(명세 forD 4절: 상시 노출)
 */
data class OverlayVisuals(
    val pointerVisibility: PointerVisibility,
    val showManualUnlock: Boolean,
) {
    companion object {
        /** 명세 forD 4절 표 기준의 상태별 포인터/해제버튼 규칙. */
        fun forState(state: ControllerState): OverlayVisuals = when (state) {
            ControllerState.ACTIVE ->
                OverlayVisuals(PointerVisibility.MOVING, showManualUnlock = false)

            ControllerState.PAUSED ->
                OverlayVisuals(PointerVisibility.FIXED_TRANSLUCENT, showManualUnlock = false)

            // 잠금 = 화면을 비운다. 포인터를 숨기고 수동 해제 버튼만 남긴다
            // (음성 "해제" 실패 시 갇힘 방지, FR-005).
            ControllerState.LOCKED ->
                OverlayVisuals(PointerVisibility.HIDDEN, showManualUnlock = true)

            ControllerState.DRAGGING ->
                OverlayVisuals(PointerVisibility.MOVING_HIGHLIGHT, showManualUnlock = false)

            ControllerState.CALIBRATING ->
                OverlayVisuals(PointerVisibility.CALIBRATION_ONLY, showManualUnlock = false)
        }
    }
}
