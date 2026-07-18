package com.mobileconductor.overlay

import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.orchestrator.calibration.CalibrationUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 클릭 성공 피드백 좌표(정규화 0~1). */
data class ClickFeedback(val x: Float, val y: Float)

/**
 * 오버레이 producer/consumer seam.
 *
 * 앱 쪽(Orchestrator/캘리브레이션)이 렌더 데이터를 여기에 publish하고, [OverlayService]가 구독해
 * 그린다. 서비스와 앱의 생명주기를 느슨하게 분리하기 위한 프로세스 내 싱글턴이다.
 * (P6 통합 시 이 지점을 정식 DI로 대체할 수 있다.)
 */
object OverlayBus {

    private val _state = MutableStateFlow(ControllerState.CALIBRATING)
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val _pointer = MutableStateFlow<PointerFrame?>(null)
    val pointer: StateFlow<PointerFrame?> = _pointer.asStateFlow()

    private val _calibration = MutableStateFlow<CalibrationUiState?>(null)
    val calibration: StateFlow<CalibrationUiState?> = _calibration.asStateFlow()

    private val _clicks = MutableSharedFlow<ClickFeedback>(extraBufferCapacity = 8)
    val clicks: SharedFlow<ClickFeedback> = _clicks.asSharedFlow()

    /** LOCKED 수동 해제 버튼 탭 시 실행될 액션(앱이 UNLOCK 주입 등으로 설정). */
    @Volatile
    var onManualUnlock: (() -> Unit)? = null

    fun publishState(state: ControllerState) { _state.value = state }
    fun publishPointer(frame: PointerFrame) { _pointer.value = frame }
    fun publishCalibration(ui: CalibrationUiState?) { _calibration.value = ui }
    fun publishClick(feedback: ClickFeedback) { _clicks.tryEmit(feedback) }
}
