package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.madcamp.handsfree.tracking.FaceTracker
import com.madcamp.handsfree.tracking.HandLandmarks
import com.madcamp.handsfree.tracking.HandTracker
import com.madcamp.handsfree.tracking.PointerTracker
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.core.model.TrackerError
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/** 입력 모드. MVP는 배타 실행(한 번에 한 트래커만 카메라를 연다). HYBRID는 Phase 4. */
enum class InputMode { FACE, HAND }

/**
 * 활성 입력 트래커의 수명과 **스위처블 D 경계 소스**를 소유한다(Phase 3).
 *
 * ## 왜 스위처블인가
 * D의 오케스트레이터·CalibrationController는 생성 시점에 `PointerSource`/명령원을 한 번
 * 붙잡고 그대로 쓴다. 런타임에 FACE↔HAND를 바꾸려면 그 **안정 객체는 그대로 두고
 * 내부가 가리키는 트래커만 바꿔야** 한다. 그래서 [pointerSource]/[gestureLandmarks]/[errors]는
 * `_active`(현재 트래커) StateFlow를 `flatMapLatest`로 따라간다 — 트래커를 갈아끼우면
 * 이 흐름들이 자동으로 새 트래커를 구독한다. **오케스트레이터·음성·오버레이는 한 줄도 안 바뀐다.**
 *
 * 배타 실행이라 전환은 이전 트래커 stop → 새 트래커 start다. 두 트래커가 동시에
 * 카메라를 여는 HYBRID는 FrameHub(Phase 4)가 필요해 여기서 하지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InputModeController(
    private val appContext: Context,
) {
    private val _mode = MutableStateFlow(InputMode.FACE)
    val mode: StateFlow<InputMode> = _mode.asStateFlow()

    private val _active = MutableStateFlow<PointerTracker?>(null)
    private val _activeHand = MutableStateFlow<HandTracker?>(null)

    /** 활성 트래커를 따라가는 포인터원. 오케스트레이터가 이 안정 객체를 계속 본다. */
    val pointerSource: PointerSource = object : PointerSource {
        override val pointerFrames: Flow<PointerFrame> =
            _active.flatMapLatest { it?.pointerFrames ?: emptyFlow() }
        override val rawFaceOrientation: Flow<RawFaceOrientation> =
            _active.flatMapLatest { it?.rawOrientations ?: emptyFlow() }
    }

    /** 활성 손 트래커를 따라가는 랜드마크 스트림. FACE 모드에서는 빈 흐름 → 제스처가 침묵한다. */
    val gestureLandmarks: Flow<HandLandmarks> =
        _activeHand.flatMapLatest { it?.handLandmarks ?: emptyFlow() }

    /** 활성 트래커의 에러 스트림(카메라 권한·모델 로딩 실패). */
    val errors: Flow<TrackerError> =
        _active.flatMapLatest { it?.errors ?: emptyFlow() }

    fun activeTracker(): PointerTracker? = _active.value

    /** 기기 회전. 활성 트래커에 즉시 반영하고, 이후 전환한 트래커에도 이어서 적용한다. */
    var displayRotationDegrees: Int = 0
        set(value) {
            field = value
            _active.value?.displayRotationDegrees = value
        }

    /**
     * 첫 기동 또는 전환. 이전 트래커를 멈추고 새 트래커를 만들어 시작한다(배타).
     * @param lifecycleOwner 카메라 바인딩 수명(서비스).
     */
    fun activate(target: InputMode, lifecycleOwner: LifecycleOwner) {
        _active.value?.stop()
        _mode.value = target
        val tracker: PointerTracker = when (target) {
            InputMode.FACE -> FaceTracker(appContext)
            InputMode.HAND -> HandTracker(appContext)
        }
        tracker.displayRotationDegrees = displayRotationDegrees
        _active.value = tracker
        _activeHand.value = tracker as? HandTracker
        tracker.start(lifecycleOwner)
        Log.i(TAG, "입력 모드 활성화 = $target")
    }

    fun stop() {
        _active.value?.stop()
        _active.value = null
        _activeHand.value = null
    }

    private companion object {
        const val TAG = "HF-InputMode"
    }
}
