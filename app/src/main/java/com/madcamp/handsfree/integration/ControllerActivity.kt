package com.madcamp.handsfree.integration

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.BuildConfig
import com.madcamp.handsfree.R
import com.madcamp.handsfree.databinding.ActivityControllerBinding
import com.madcamp.handsfree.telemetry.LocalTelemetryQueue
import com.madcamp.handsfree.telemetry.Telemetry
import com.madcamp.handsfree.telemetry.TelemetryCrashHandler
import com.madcamp.handsfree.telemetry.TelemetrySettings
import com.madcamp.handsfree.telemetry.TelemetryUploadWorker
import com.mobileconductor.overlay.OverlayService
import kotlinx.coroutines.launch

/**
 * 설정 화면. **파이프라인을 소유하지 않는다.**
 *
 * 카메라/음성/오케스트레이터는 [ControllerPipeline]이 들고 있고 그 수명은
 * [OverlayService]에 묶여 있다. 이 Activity는 세 가지만 한다:
 * 권한 확보 · 서비스 기동 · 캘리브레이션 트리거.
 *
 * 그래서 **이 화면을 나가도 포인터가 계속 움직인다.** 예전에는 여기서 카메라를
 * 켰는데, CameraX가 Activity 라이프사이클에 묶여서 앱을 나가면 얼어붙었다.
 */
class ControllerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControllerBinding
    private lateinit var telemetrySettings: TelemetrySettings
    private lateinit var telemetryQueue: LocalTelemetryQueue

    /** C의 접근성 서비스 컴포넌트. 실제 구현 패키지는 통합 이전 그대로다(OPEN_ISSUES 참고). */
    private val accessibilityServiceComponent by lazy {
        ComponentName(packageName, "com.example.hands_free_controller.service.GestureAccessibilityService")
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val telemetryLogger = Telemetry.logger(applicationContext)
        if (granted.values.all { it }) {
            startControllerAndCalibrate()
        } else {
            if (granted[Manifest.permission.RECORD_AUDIO] == false) {
                telemetryLogger.logAppError("MIC_PERMISSION_DENIED")
            }
            if (granted[Manifest.permission.CAMERA] == false) {
                telemetryLogger.logAppError("CAMERA_PERMISSION_DENIED")
            }
            applyStatus(
                R.string.status_permission_denied,
                R.color.status_denied_bg,
                R.color.status_denied_fg,
                R.color.status_denied_dot,
            )
            updateTelemetryStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TelemetryCrashHandler.install(applicationContext)
        TelemetryUploadWorker.scheduleDailyAt9(applicationContext)

        telemetrySettings = TelemetrySettings(applicationContext)
        telemetryQueue = LocalTelemetryQueue(applicationContext)

        binding.stepAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.stepOverlay.setOnClickListener { requestOverlayPermission() }
        binding.stepStart.setOnClickListener {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }

        // 사용법 가이드는 진행 상태와 무관하게 언제든 열람 가능해야 한다(구현 지시).
        binding.usageGuideToggle.setOnClickListener {
            val expanding = binding.usageGuideContent.visibility != View.VISIBLE
            binding.usageGuideContent.visibility = if (expanding) View.VISIBLE else View.GONE
            binding.usageGuideArrow.text = if (expanding) "▴" else "▾"
        }

        // 저장된 프로파일을 버리고 처음부터 다시 잡는다.
        // 자세나 거치 위치가 바뀌면 기존 범위가 안 맞는다.
        binding.btnRecalibrate.setOnClickListener {
            CalibrationStore.clear(this)
            ControllerPipeline.runCalibration()
        }
        // 알림의 정지 버튼과 같은 동작. 서비스가 죽으면 파이프라인도 같이 정리된다.
        binding.btnStop.setOnClickListener {
            OverlayService.stop(this)
            applyStatus(R.string.status_idle, R.color.status_idle_bg, R.color.status_idle_fg, R.color.status_idle_dot)
            refreshStepStates()
        }

        val telemetryLogger = Telemetry.logger(applicationContext)
        binding.checkboxTelemetryConsent.isChecked = telemetrySettings.diagnosticsEnabled
        binding.checkboxTelemetryConsent.setOnCheckedChangeListener { _, isChecked ->
            telemetrySettings.diagnosticsEnabled = isChecked
            if (isChecked) {
                telemetryLogger.logAppOpened()
            }
            updateTelemetryStatus()
        }
        telemetryLogger.logAppOpened()

        binding.btnSendFeedback.setOnClickListener {
            val message = binding.feedbackMessage.text.toString().trim()
            val situation = binding.feedbackSituation.text.toString().trim()
            if (message.isBlank() && situation.isBlank()) {
                binding.telemetryStatusText.setText(R.string.telemetry_feedback_empty)
                return@setOnClickListener
            }
            telemetryLogger.logUserFeedback(message, situation)
            binding.feedbackMessage.text.clear()
            binding.feedbackSituation.text.clear()
            binding.telemetryStatusText.text = getString(
                R.string.telemetry_feedback_saved,
                telemetryQueue.count(),
            )
        }
        // 업로드 테스트 버튼은 개발용이다. 낯선 사용자에게 배포하는 화면에
        // "Logcat", "Firebase" 같은 말이 보이면 앱이 미완성으로 읽힌다.
        // 기능을 지우지 않고 숨기기만 하는 이유는 디버그 빌드에서 계속 쓰기 때문이다.
        if (BuildConfig.DEBUG) {
            binding.btnUploadTest.setOnClickListener {
                TelemetryUploadWorker.enqueueManualTestUpload(
                    context = applicationContext,
                    useLogcatUploader = true,
                )
                binding.telemetryStatusText.setText(R.string.telemetry_upload_test_started)
            }
            binding.btnUploadFirebaseTest.setOnClickListener {
                TelemetryUploadWorker.enqueueManualTestUpload(
                    context = applicationContext,
                    useLogcatUploader = false,
                )
                binding.telemetryStatusText.setText(R.string.telemetry_firebase_upload_test_started)
            }
        } else {
            binding.btnUploadTest.visibility = View.GONE
            binding.btnUploadFirebaseTest.visibility = View.GONE
        }
        updateTelemetryStatus()
        refreshStepStates()

        lifecycleScope.launch {
            ControllerPipeline.calibrating.collect { running ->
                when {
                    running -> applyStatus(
                        R.string.status_calibrating,
                        R.color.status_calibrating_bg,
                        R.color.status_calibrating_fg,
                        R.color.status_calibrating_dot,
                    )
                    ControllerPipeline.isRunning -> applyStatus(
                        R.string.status_active,
                        R.color.status_active_bg,
                        R.color.status_active_fg,
                        R.color.status_active_dot,
                    )
                    else -> applyStatus(
                        R.string.status_idle,
                        R.color.status_idle_bg,
                        R.color.status_idle_fg,
                        R.color.status_idle_dot,
                    )
                }
                refreshStepStates()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 접근성/오버레이 권한은 설정 화면에서 바뀌고 돌아오므로, 돌아올 때마다 다시 확인해야 한다.
        refreshStepStates()
    }

    /** 상태 배지의 텍스트·배경·점 색을 한 번에 맞춘다. */
    private fun applyStatus(labelRes: Int, bgColorRes: Int, fgColorRes: Int, dotColorRes: Int) {
        binding.statusText.setText(labelRes)
        binding.statusText.setTextColor(ContextCompat.getColor(this, fgColorRes))
        binding.statusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgColorRes))
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, dotColorRes))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { raw -> ComponentName.unflattenFromString(raw) == accessibilityServiceComponent }
    }

    /**
     * 3단계 순서형 카드의 완료/진행/잠김 상태를 실제 권한 상태 기준으로 갱신한다.
     *
     * **건너뛰기를 막는다(구현 지시)** — 이전 단계가 끝나야 다음 단계 카드가 눌린다.
     */
    private fun refreshStepStates() {
        val accessibilityDone = isAccessibilityServiceEnabled()
        val overlayDone = Settings.canDrawOverlays(this)
        val startDone = ControllerPipeline.isRunning

        setStepState(binding.stepAccessibility, binding.stepAccessibilityNumber, "1", done = accessibilityDone, locked = false)
        setStepState(binding.stepOverlay, binding.stepOverlayNumber, "2", done = overlayDone, locked = !accessibilityDone)
        setStepState(binding.stepStart, binding.stepStartNumber, "3", done = startDone, locked = !overlayDone)
    }

    private fun setStepState(row: View, number: TextView, index: String, done: Boolean, locked: Boolean) {
        row.isEnabled = !locked
        row.isClickable = !locked
        row.alpha = if (locked) 0.45f else 1f
        val circleColorRes = if (done) R.color.status_active_dot else R.color.accent_ink
        number.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, circleColorRes))
        number.text = if (done) "✓" else index
    }

    private fun updateTelemetryStatus() {
        binding.telemetryStatusText.text = if (telemetrySettings.diagnosticsEnabled) {
            getString(R.string.telemetry_status_enabled, telemetryQueue.count())
        } else {
            getString(R.string.telemetry_status_disabled)
        }
    }

    /** 오버레이 권한이 없으면 설정으로 보낸다. 있으면 바로 서비스를 띄운다. */
    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }

    /**
     * 권한이 다 모인 뒤 서비스를 띄우고 캘리브레이션을 시작한다.
     *
     * 서비스에는 권한을 요청할 방법이 없어서 **Activity가 먼저 받아둔 다음** 켜야 한다.
     * 순서가 뒤집히면 카메라가 조용히 실패한다.
     */
    private fun startControllerAndCalibrate() {
        if (!Settings.canDrawOverlays(this)) {
            applyStatus(
                R.string.status_need_overlay,
                R.color.status_progress_bg,
                R.color.status_progress_fg,
                R.color.status_progress_dot,
            )
            requestOverlayPermission()
            return
        }
        startOverlayService()
        ControllerPipeline.updateRotation(currentRotationDegrees())
        // 저장된 프로파일이 있으면 22초짜리 보정을 다시 시키지 않는다
        ControllerPipeline.runCalibrationIfNeeded(this)
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    /**
     * 회전 보정은 A가 흡수한다. 각도만 알려주면 C/D는 가로/세로를 신경 쓰지 않는다
     * (OPEN_ISSUES #5).
     */
    private fun currentRotationDegrees(): Int {
        // Activity.getDisplay()는 API 30부터다. minSdk가 26이라 하위에서는
        // deprecated된 defaultDisplay로 떨어진다 — 실기기 검증도 API 26에서 했다.
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ControllerPipeline.updateRotation(currentRotationDegrees())
    }

    // onDestroy를 재정의하지 않는다 — 의도적이다.
    // 이 화면을 벗어나도 컨트롤러는 계속 돌아야 한다. 파이프라인 정지는
    // OverlayService.onDestroy가 담당한다.
}
