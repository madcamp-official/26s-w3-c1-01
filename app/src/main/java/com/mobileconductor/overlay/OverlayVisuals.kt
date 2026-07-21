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
 * 상태 → 오버레이 시각 요소 매핑 결과. 순수 데이터라 상태별 렌더 규칙을 유닛 테스트할 수 있다(DoD 4).
 *
 * @param pointerVisibility 포인터 표시 방식
 * @param indicatorColor 인디케이터 색상
 * @param label 인디케이터 옆 텍스트("일시정지"/"잠김"/"드래그 중"). 없으면 null
 * @param showManualUnlock LOCKED에서 수동 잠금 해제 버튼 노출 여부(명세 forD 4절: 상시 노출)
 * @param showStatusHud 상단 상태 인디케이터(점+라벨) 노출 여부. LOCKED는 화면을 비우려고 끈다 —
 *   단 수동 해제 버튼([showManualUnlock])은 갇힘 방지를 위해 별개로 항상 남긴다(FR-005).
 */
data class OverlayVisuals(
    val pointerVisibility: PointerVisibility,
    val indicatorColor: IndicatorColor,
    val label: String?,
    val showManualUnlock: Boolean,
    val showStatusHud: Boolean,
) {
    companion object {
        /** 명세 forD 4절 표 그대로의 상태별 시각 규칙. */
        fun forState(state: ControllerState): OverlayVisuals = when (state) {
            ControllerState.ACTIVE -> OverlayVisuals(
                pointerVisibility = PointerVisibility.MOVING,
                indicatorColor = IndicatorColor.GREEN,
                label = null,
                showManualUnlock = false,
                showStatusHud = true,
            )

            ControllerState.PAUSED -> OverlayVisuals(
                pointerVisibility = PointerVisibility.FIXED_TRANSLUCENT,
                indicatorColor = IndicatorColor.YELLOW,
                label = "일시정지",
                showManualUnlock = false,
                showStatusHud = true,
            )

            // 잠금 = 화면을 비운다. 포인터·상태 인디케이터·라벨 전부 숨기고
            // 오직 수동 해제 버튼만 남긴다(음성 "해제" 실패 시 갇힘 방지, FR-005).
            ControllerState.LOCKED -> OverlayVisuals(
                pointerVisibility = PointerVisibility.HIDDEN,
                indicatorColor = IndicatorColor.GRAY,
                label = "잠김",
                showManualUnlock = true,
                showStatusHud = false,
            )

            ControllerState.DRAGGING -> OverlayVisuals(
                pointerVisibility = PointerVisibility.MOVING_HIGHLIGHT,
                indicatorColor = IndicatorColor.BLUE,
                label = "드래그 중",
                showManualUnlock = false,
                showStatusHud = true,
            )

            ControllerState.CALIBRATING -> OverlayVisuals(
                pointerVisibility = PointerVisibility.CALIBRATION_ONLY,
                indicatorColor = IndicatorColor.PROGRESS,
                label = null,
                showManualUnlock = false,
                showStatusHud = true,
            )
        }
    }
}
