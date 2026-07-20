package com.madcamp.handsfree.tracking

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.core.model.TrackerError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HAND 모드의 진입점. 전면 카메라 → 손 랜드마크 → PointerFrame 방출.
 * A의 [FaceTracker]와 **대칭 구조**다 (같은 Flow 규약, 같은 카메라 배선, 같은 단조 시계).
 *
 * **컨트롤러 상태(ACTIVE/PAUSED/LOCKED)를 전혀 모른다.** FaceTracker와 같은 이유로,
 * 상태에 따라 포인터를 거르는 것은 D의 몫이다.
 *
 * ## 계약 재사용 (MOTION_CAPTURE_PLAN 축 1)
 * 손의 **화면 방향 위치**(hx, hy ∈ 0~1)를 [RawFaceOrientation]의 `yaw`/`pitch` 슬롯에
 * 그대로 싣는다. 그러면 D의 [com.mobileconductor.orchestrator.calibration.CalibrationController]가
 * 얼굴과 똑같은 9점 수집으로 손 도달범위를 만든다 — CalibrationController는 min/max만
 * 보므로 얼굴/손을 구분할 필요가 없다.
 *
 * ## MVP 범위 (Phase 1)
 * **포인터만** 방출한다. 제스처 판정(Phase 2)은 여기서 랜드마크를 노출해 붙일 예정이다
 * ([onResult]의 seam 주석 참고). HYBRID(얼굴+손 동시)는 Phase 4의 FrameHub 몫이라,
 * 지금은 FaceTracker처럼 **자기 카메라를 직접 소유**한다(모드 배타 실행).
 */
class HandTracker(
    private val context: Context,
) : PointerTracker {

    /** HAND → C, HAND → D */
    private val _pointerFrames = MutableSharedFlow<PointerFrame>(
        replay = 1,
        // 소비 측이 느려도 트래킹이 밀리면 안 된다. 오래된 좌표는 버린다 (FaceTracker와 동일)
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pointerFrames: SharedFlow<PointerFrame> = _pointerFrames.asSharedFlow()

    /** HAND → D (캘리브레이션 전용). 프로파일이 없어도 방출된다 */
    private val _rawOrientations = MutableSharedFlow<RawFaceOrientation>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val rawOrientations: SharedFlow<RawFaceOrientation> = _rawOrientations.asSharedFlow()

    private val _errors = MutableSharedFlow<TrackerError>(replay = 1)
    override val errors: SharedFlow<TrackerError> = _errors.asSharedFlow()

    /**
     * HAND 전용 — 제스처 판정용 랜드마크 스트림(Phase 2). [PointerTracker]에는 없다
     * (FACE는 제스처가 없으므로). 손 미검출 프레임은 빈 points로 방출해 분류기가 리셋하게 한다.
     */
    private val _handLandmarks = MutableSharedFlow<HandLandmarks>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val handLandmarks: SharedFlow<HandLandmarks> = _handLandmarks.asSharedFlow()

    private val mapper = HandLandmarkMapper()
    private var landmarker: HandLandmarker? = null
    private var executor: ExecutorService? = null

    /** 기기 회전은 여기서 흡수한다. 소비 측은 가로/세로를 신경 쓰지 않는다 (FaceTracker와 동일) */
    @Volatile
    override var displayRotationDegrees: Int = 0

    private var lastLowLight = false

    /** 디버그 화면 전용. 손이 실제로 검출되는지 확인용 */
    @Volatile
    var lastHandDetected: Boolean = false
        private set

    override fun updateProfile(profile: CalibrationProfile) = mapper.updateProfile(profile)

    override fun start(lifecycleOwner: LifecycleOwner) {
        if (!initLandmarker()) return

        val exec = Executors.newSingleThreadExecutor().also { executor = it }
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val provider = try {
                providerFuture.get()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 프로바이더 획득 실패", e)
                emitError(TrackerError.Type.CAMERA_UNAVAILABLE)
                return@addListener
            }

            val analysis = ImageAnalysis.Builder()
                // 최신 프레임만 본다. 큐에 쌓이면 지연이 누적돼 200ms NFR을 못 지킨다
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .apply { setAnalyzer(exec, ::analyze) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "카메라 바인딩 실패", e)
                emitError(TrackerError.Type.CAMERA_UNAVAILABLE)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        executor?.shutdown()
        executor = null
        landmarker?.close()
        landmarker = null
    }

    private fun initLandmarker(): Boolean {
        return try {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                // 한 손만 조작에 쓴다. 여럿이 잡히면 MediaPipe가 신뢰도 높은 쪽을 준다
                .setNumHands(1)
                .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setResultListener { result, _ -> onResult(result) }
                .setErrorListener { e -> Log.e(TAG, "랜드마커 오류", e) }
                .build()

            landmarker = HandLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            // 모델 파일(assets/hand_landmarker.task)이 없으면 여기로 온다
            Log.e(TAG, "랜드마커 초기화 실패 — assets/$MODEL_ASSET 확인", e)
            emitError(TrackerError.Type.MODEL_LOAD_FAILED)
            false
        }
    }

    private var frameIndex = 0

    private fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toUprightBitmap()
            // 휘도 계산은 비싸다. 조명은 초 단위로 변하니 10프레임에 한 번이면 충분하다 (FaceTracker와 동일)
            if (frameIndex++ % LOW_LIGHT_CHECK_INTERVAL == 0) {
                lastLowLight = bitmap.isLowLight()
            }
            val mpImage = BitmapImageBuilder(bitmap).build()
            // 벽시계가 아니라 단조 시계. B/C와 시계가 섞이면 D의 순서 판단이 틀어진다 (OPEN_ISSUES #7)
            landmarker?.detectAsync(mpImage, SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            Log.e(TAG, "프레임 분석 실패", e)
        } finally {
            // 닫지 않으면 두세 프레임 만에 카메라가 멈춘다
            image.close()
        }
    }

    private fun onResult(result: HandLandmarkerResult) {
        val timestamp = SystemClock.elapsedRealtime()
        val hands = result.landmarks()
        val hasHand = hands.isNotEmpty() && hands[0].size > INDEX_FINGER_TIP
        lastHandDetected = hasHand

        if (!hasHand) {
            mapper.onHandLost()
            // 빈 랜드마크 = 손 미검출. 제스처 분류기가 궤적/유지 상태를 리셋한다.
            _handLandmarks.tryEmit(HandLandmarks(emptyList(), 0.5f, 0.5f, timestamp))
            emit(
                RawFaceOrientation(
                    timestamp = timestamp,
                    yaw = 0f, pitch = 0f,
                    eyeOffsetX = 0f, eyeOffsetY = 0f,
                    faceDetected = false,
                    confidence = 0f,
                )
            )
            return
        }

        // 포인터 기준점 = 검지 끝(#8). MediaPipe 정규화 좌표(0~1): x 좌→우, y 상→하(이미지 기준)
        val tip = hands[0][INDEX_FINGER_TIP]
        val (hx, hy) = toScreenSpace(tip.x(), tip.y())

        // 제스처 판정(Phase 2)용 랜드마크 방출. 손 모양은 원시 21점, 스와이프 방향은 화면 좌표 tip.
        _handLandmarks.tryEmit(
            HandLandmarks(
                points = hands[0].map { HandLandmarks.Point(it.x(), it.y(), it.z()) },
                screenTipX = hx,
                screenTipY = hy,
                timestamp = timestamp,
            )
        )

        emit(
            RawFaceOrientation(
                timestamp = timestamp,
                // 손의 화면 방향 위치를 yaw/pitch 슬롯에 싣는다(계약 재사용, 클래스 주석 참고)
                yaw = hx,
                pitch = hy,
                eyeOffsetX = 0f,
                eyeOffsetY = 0f,
                faceDetected = true,
                // HandLandmarker도 얼굴처럼 단일 검출 점수를 주지 않는다. 검출=임계값 통과라 1.0.
                confidence = 1f,
            )
        )
    }

    private fun emit(raw: RawFaceOrientation) {
        _rawOrientations.tryEmit(raw)
        _pointerFrames.tryEmit(mapper.map(raw, lastLowLight))
    }

    private fun emitError(type: TrackerError.Type) {
        _errors.tryEmit(TrackerError(type, SystemClock.elapsedRealtime()))
    }

    /**
     * 이미지 공간 손 좌표(lx, ly ∈ 0~1) → 화면 방향 위치(hx, hy ∈ 0~1).
     *
     * 1) **미러링**: 전면 카메라라 좌우가 뒤집힐 수 있다. [MIRROR_X]가 규약을 흡수한다 —
     *    "손을 사용자 오른쪽으로 → hx 증가"가 되도록. **부호가 틀리면 포인터가 손과 반대로
     *    움직인다**(SPEC §11-2). 실기기에서 이 상수 하나만 뒤집어 고친다. FaceTracker의
     *    YAW_SIGN과 같은 성격의, 컴파일로는 안 잡히는 값이다.
     * 2) **회전**: 기기가 누우면 이미지 축과 화면 축이 어긋난다. 정규화 점을 회전시켜 흡수한다.
     *    FaceTracker.applyDisplayRotation의 좌표 버전이다(각도가 아니라 점이라 식이 다르다).
     */
    private fun toScreenSpace(lx: Float, ly: Float): Pair<Float, Float> {
        val mx = if (MIRROR_X) 1f - lx else lx
        val my = ly
        return when (displayRotationDegrees) {
            90 -> my to (1f - mx)
            180 -> (1f - mx) to (1f - my)
            270 -> (1f - my) to mx
            else -> mx to my
        }
    }

    private companion object {
        const val TAG = "HF-HandTracker"
        const val MODEL_ASSET = "hand_landmarker.task"
        const val LOW_LIGHT_CHECK_INTERVAL = 10

        /** HandLandmarker 랜드마크 인덱스: 검지 끝. */
        const val INDEX_FINGER_TIP = 8

        /**
         * 전면 카메라 좌우 반전 흡수. **실기기에서 포인터가 손과 반대로 움직이면 뒤집는다.**
         * 기본 true = 셀피 프리뷰처럼 미러링된 좌표계를 가정.
         */
        const val MIRROR_X = true

        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
}
