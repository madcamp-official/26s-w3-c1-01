package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState

/** 포인터 표시 방식. */
enum class PointerVisibility {
    /** 실시간 이동, 기본 색상 (ACTIVE). */
    MOVING,

    /** 숨김 (LOCKED). */
    HIDDEN,

    /** 포인터 대신 기준점 전용 UI (CALIBRATING). */
    CALIBRATION_ONLY,
}

/** 캘리브레이션 렌더링 등에 쓰는 색상 토큰. 실제 ARGB는 렌더 레이어에서 매핑. */
enum class IndicatorColor { GREEN, GRAY, PROGRESS }

/**
 * 상태 → 오버레이 시각 규칙 매핑 결과. 순수 데이터라 상태별 규칙을 유닛 테스트할 수 있다.
 *
 * 상단 상태 인디케이터(점+라벨)는 제거했다. 활성은 포인터 흰 링, 비활성은 회색 링,
 * 잠금은 수동 해제 버튼으로 구분한다.
 */
data class OverlayVisuals(
    val pointerVisibility: PointerVisibility,
    val showManualUnlock: Boolean,
) {
    companion object {
        fun forState(state: ControllerState): OverlayVisuals = when (state) {
            ControllerState.ACTIVE ->
                OverlayVisuals(PointerVisibility.MOVING, showManualUnlock = false)

            ControllerState.LOCKED ->
                OverlayVisuals(PointerVisibility.HIDDEN, showManualUnlock = true)

            ControllerState.CALIBRATING ->
                OverlayVisuals(PointerVisibility.CALIBRATION_ONLY, showManualUnlock = false)
        }
    }
}
