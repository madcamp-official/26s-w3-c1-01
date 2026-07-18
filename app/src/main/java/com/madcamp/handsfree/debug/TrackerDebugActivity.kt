package com.madcamp.handsfree.debug

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.madcamp.handsfree.contract.CalibrationProfile.Level
import com.madcamp.handsfree.databinding.ActivityTrackerDebugBinding
import com.madcamp.handsfree.tracking.FaceTracker
import kotlinx.coroutines.launch

/**
 * A파트 검증 화면. **통합 시 D의 오버레이로 대체되고 사라진다.**
 *
 * 완료 기준(DoD)을 눈으로 확인하기 위한 것들만 있다:
 * 포인터 위치 / 실측 fps / faceDetected / 감도·스무딩 3단계 전환.
 */
class TrackerDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackerDebugBinding
    private lateinit var tracker: FaceTracker

    private var sensitivity = Level.MID
    private var smoothing = Level.MID

    /** 시선 보조 세기 후보. 1.5까지 열어둬서 "눈만으로 조작"이 가능한지 체감해 본다 */
    private val GAZE_WEIGHTS = floatArrayOf(0f, 0.5f, 1.0f, 1.5f)
    private var gazeIndex = 1

    // fps 실측용. "15fps 이상"이 NFR이라 추정이 아니라 세어봐야 한다
    private var frameCount = 0
    private var windowStart = 0L
    private var measuredFps = 0f

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            tracker.start(this)
        } else {
            binding.statusText.text = "카메라 권한이 거부되었습니다"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tracker = FaceTracker(applicationContext)
        applyProfile()

        binding.sensitivityButton.setOnClickListener {
            sensitivity = sensitivity.next()
            applyProfile()
        }
        binding.smoothingButton.setOnClickListener {
            smoothing = smoothing.next()
            applyProfile()
        }
        binding.gazeButton.setOnClickListener {
            // 0을 포함시킨 이유: 시선을 완전히 끈 상태와 비교해야
            // "지금 움직이는 게 눈 때문인지 고개 때문인지"를 구분할 수 있다
            gazeIndex = (gazeIndex + 1) % GAZE_WEIGHTS.size
            tracker.gazeAssistWeight = GAZE_WEIGHTS[gazeIndex]
            binding.gazeButton.text = "시선 %.1f".format(GAZE_WEIGHTS[gazeIndex])
        }

        observeTracker()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tracker.start(this)
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // 회전 흡수는 A 책임이라 현재 회전을 트래커에 알려준다 (OPEN_ISSUES #5)
        tracker.displayRotationDegrees = when (windowManager.defaultDisplay.rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    override fun onDestroy() {
        tracker.stop()
        super.onDestroy()
    }

    private fun applyProfile() {
        tracker.updateProfile(MockCalibration.profile(sensitivity, smoothing))
        binding.sensitivityButton.text = "감도: ${sensitivity.label()}"
        binding.smoothingButton.text = "스무딩: ${smoothing.label()}"
    }

    private fun observeTracker() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    tracker.pointerFrames.collect { frame ->
                        binding.overlay.update(frame)
                        countFps()
                        binding.statusText.text = buildString {
                            append("x=%.3f y=%.3f".format(frame.x, frame.y))
                            append("  fps=%.1f".format(measuredFps))
                            append(if (frame.faceDetected) "  얼굴 O" else "  얼굴 X")
                            if (frame.lowLight) append("  저조도")
                        }
                    }
                }
                launch {
                    tracker.rawOrientations.collect { raw ->
                        // 랜드마크 개수를 같이 띄운다. 478이면 홍채(468~477)가 오는 것이고,
                        // 468이면 홍채 없는 모델이라 시선 보조가 동작할 수 없다.
                        binding.rawText.text =
                            "yaw=%.1f pitch=%.1f eye=(%.2f, %.2f) 랜드마크=%d".format(
                                raw.yaw, raw.pitch, raw.eyeOffsetX, raw.eyeOffsetY,
                                tracker.lastLandmarkCount,
                            )
                    }
                }
                launch {
                    tracker.errors.collect { error ->
                        binding.statusText.text = "오류: ${error.type}"
                    }
                }
            }
        }
    }

    private fun countFps() {
        val now = SystemClock.elapsedRealtime()
        if (windowStart == 0L) windowStart = now
        frameCount++
        val elapsed = now - windowStart
        if (elapsed >= 1000L) {
            measuredFps = frameCount * 1000f / elapsed
            frameCount = 0
            windowStart = now
        }
    }

    private fun Level.next() = when (this) {
        Level.LOW -> Level.MID
        Level.MID -> Level.HIGH
        Level.HIGH -> Level.LOW
    }

    private fun Level.label() = when (this) {
        Level.LOW -> "낮음"
        Level.MID -> "중간"
        Level.HIGH -> "높음"
    }
}
