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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
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
 * A파트의 진입점. 전면 카메라 → 얼굴 랜드마크 → PointerFrame 방출.
 *
 * **컨트롤러 상태(ACTIVE/PAUSED/LOCKED)를 전혀 모른다.** PAUSED에서 포인터를
 * 숨길지는 D가 이 출력을 걸러서 정한다. "PAUSED면 방출을 멈추자"고 고치는 순간
 * A가 D의 상태 머신 완성을 안 기다려도 되는 이유가 사라진다.
 */
class FaceTracker(
    private val context: Context,
) : PointerTracker {

    /** A → C, A → D */
    private val _pointerFrames = MutableSharedFlow<PointerFrame>(
        replay = 1,
        // 소비 측이 느려도 트래킹이 밀리면 안 된다. 오래된 좌표는 버리는 게 맞다
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pointerFrames: SharedFlow<PointerFrame> = _pointerFrames.asSharedFlow()

    /** A → D (캘리브레이션 전용). 프로파일이 없어도 방출된다 */
    private val _rawOrientations = MutableSharedFlow<RawFaceOrientation>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val rawOrientations: SharedFlow<RawFaceOrientation> = _rawOrientations.asSharedFlow()

    private val _errors = MutableSharedFlow<TrackerError>(replay = 1)
    override val errors: SharedFlow<TrackerError> = _errors.asSharedFlow()

    private val mapper = PointerMapper()
    private var landmarker: FaceLandmarker? = null
    private var executor: ExecutorService? = null

    /** 기기 회전은 A가 흡수한다. 소비 측은 가로/세로를 신경 쓰지 않는다 (OPEN_ISSUES #5) */
    @Volatile
    override var displayRotationDegrees: Int = 0

    private var lastLowLight = false

    /**
     * 눈을 감은 동안 쓸 직전 시선값.
     *
     * 깜빡임은 0.1~0.3초인데 그동안 시선을 0으로 밀면 포인터가 중앙으로 튀었다가
     * 돌아온다. 사람은 자기가 깜빡였다고 포인터가 움직일 거라 기대하지 않는다 —
     * 그래서 감은 동안은 직전 값을 그대로 유지한다.
     */
    private var lastGaze = EyeOffset.Gaze(0f, 0f, eyesOpen = true)

    /** 디버그 화면 전용. 홍채 랜드마크(468~477)가 실제로 오는지 확인하려고 노출한다 */
    @Volatile
    var lastLandmarkCount: Int = 0
        private set

    override fun updateProfile(profile: CalibrationProfile) = mapper.updateProfile(profile)

    /** 디버그 화면에서 시선 보조 세기를 바꿔 보기 위한 통로. 값이 정해지면 없앤다 */
    var gazeAssistWeight: Float
        get() = mapper.gazeAssistWeight
        set(value) { mapper.gazeAssistWeight = value }

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

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                // 자세 추정의 핵심. 이걸 끄면 랜드마크 위치로 각도를 직접 계산해야 한다
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener { result, _ -> onResult(result) }
                .setErrorListener { e -> Log.e(TAG, "랜드마커 오류", e) }
                .build()

            landmarker = FaceLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            // 모델 파일(assets/face_landmarker.task)이 없으면 여기로 온다
            Log.e(TAG, "랜드마커 초기화 실패 — assets/$MODEL_ASSET 확인", e)
            emitError(TrackerError.Type.MODEL_LOAD_FAILED)
            false
        }
    }

    private var frameIndex = 0

    private fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toUprightBitmap()
            // 휘도 계산은 getPixel을 수백 번 부르는 작업이라 매 프레임 하면 fps를 깎는다.
            // 조명은 초 단위로 변하는 값이라 10프레임에 한 번이면 충분하다.
            if (frameIndex++ % LOW_LIGHT_CHECK_INTERVAL == 0) {
                lastLowLight = bitmap.isLowLight()
            }
            val mpImage = BitmapImageBuilder(bitmap).build()
            // 벽시계가 아니라 단조 시계를 쓴다. B/C와 시계가 섞이면 D의 순서
            // 판단이 조용히 틀어진다 (OPEN_ISSUES #7)
            landmarker?.detectAsync(mpImage, SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            Log.e(TAG, "프레임 분석 실패", e)
        } finally {
            // 닫지 않으면 두세 프레임 만에 카메라가 멈춘다
            image.close()
        }
    }

    private fun onResult(result: FaceLandmarkerResult) {
        val timestamp = SystemClock.elapsedRealtime()
        val matrices = result.facialTransformationMatrixes()
        val landmarks = result.faceLandmarks()

        val hasFace = landmarks.isNotEmpty() &&
            matrices.isPresent && matrices.get().isNotEmpty()

        if (!hasFace) {
            mapper.onFaceLost()
            val raw = RawFaceOrientation(
                timestamp = timestamp,
                yaw = 0f, pitch = 0f,
                eyeOffsetX = 0f, eyeOffsetY = 0f,
                faceDetected = false,
                confidence = 0f,
            )
            emit(raw)
            return
        }

        // facialTransformationMatrixes()는 Optional<List<float[]>>다 —
        // 원소가 이미 float[16]이라 별도 래퍼 객체를 거치지 않는다
        val (rawYaw, rawPitch) = HeadPose.fromTransformMatrix(matrices.get()[0])
        val (yaw, pitch) = applyDisplayRotation(rawYaw, rawPitch)

        lastLandmarkCount = landmarks[0].size
        val gaze = EyeOffset.from(landmarks[0])
        // 눈을 감은 프레임은 버리고 직전 값을 유지한다
        if (gaze.eyesOpen) lastGaze = gaze
        val eye = lastGaze

        val raw = RawFaceOrientation(
            timestamp = timestamp,
            yaw = yaw,
            pitch = pitch,
            eyeOffsetX = eye.x,
            eyeOffsetY = eye.y,
            faceDetected = true,
            // MediaPipe FaceLandmarker는 얼굴별 검출 점수를 따로 주지 않는다.
            // 검출됐다는 사실 자체가 임계값을 넘었다는 뜻이라 1.0으로 둔다.
            // D가 confidence 임계값 정책을 쓰려면 이 값의 의미를 합의해야 한다.
            confidence = 1f,
        )
        emit(raw)
    }

    private fun emit(raw: RawFaceOrientation) {
        _rawOrientations.tryEmit(raw)
        _pointerFrames.tryEmit(mapper.map(raw, lastLowLight))
    }

    private fun emitError(type: TrackerError.Type) {
        _errors.tryEmit(TrackerError(type, SystemClock.elapsedRealtime()))
    }

    /**
     * 기기가 가로로 누우면 사용자의 "고개 좌우"가 화면의 세로축이 된다.
     * 이걸 A에서 흡수해두면 C/D는 회전을 아예 몰라도 된다.
     */
    private fun applyDisplayRotation(yaw: Float, pitch: Float): Pair<Float, Float> =
        when (displayRotationDegrees) {
            90 -> pitch to -yaw
            180 -> -yaw to -pitch
            270 -> -pitch to yaw
            else -> yaw to pitch
        }

    private companion object {
        const val TAG = "HF-Tracker"
        const val MODEL_ASSET = "face_landmarker.task"
        const val LOW_LIGHT_CHECK_INTERVAL = 10
    }
}
