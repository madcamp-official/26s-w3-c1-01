package com.madcamp.handsfree.integration

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 *
 * 화면은 두 개다: 초기 설정(screen_setup, 앱 이름 + 3단계)과 메인(screen_main).
 * setupComplete 여부로 전환하며, 설정이 하나라도 꺼지면 설정 화면으로 되돌아간다.
 */
class ControllerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControllerBinding
    private lateinit var telemetrySettings: TelemetrySettings
    private lateinit var telemetryQueue: LocalTelemetryQueue

    // 3단계(초기 얼굴인식 설정)에서 시작한 보정이 끝나면 서비스를 내려 "대기" 상태로 둔다.
    // 화면2로 넘어올 때 자동으로 켜지지 않고, 사용자가 직접 "시작"을 누르게 하기 위함.
    private var stopAfterInitialCalibration = false

    /** C의 접근성 서비스 컴포넌트. 실제 구현 패키지는 통합 이전 그대로다(OPEN_ISSUES 참고). */
    private val accessibilityServiceComponent by lazy {
        ComponentName(packageName, "com.example.hands_free_controller.service.GestureAccessibilityService")
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val telemetryLogger = Telemetry.logger(applicationContext)
        if (granted.values.all { it }) {
            if (calibrationDone()) {
                // 보정 프로파일이 이미 있다(예: 카메라·마이크만 껐다 다시 켠 경우) → 보정이 필요 없다.
                // 이때 startControllerAndCalibrate를 부르면 보정을 건너뛴 채 서비스만 켜져서
                // 화면2로 넘어갈 때 자동으로 포인터가 떠 버린다. 그러니 서비스를 켜지 않는다
                // (떠 있으면 내려서 "대기" 상태로 둔다). 사용자가 화면2에서 "시작"을 눌러야 켜진다.
                OverlayService.stop(this)
            } else {
                // 첫 보정 필요 → 서비스를 켜고 초기 보정. 보정이 끝나면 collector가 서비스를 내린다.
                stopAfterInitialCalibration = true
                startControllerAndCalibrate()
            }
        } else {
            if (granted[Manifest.permission.RECORD_AUDIO] == false) {
                telemetryLogger.logAppError("MIC_PERMISSION_DENIED")
            }
            if (granted[Manifest.permission.CAMERA] == false) {
                telemetryLogger.logAppError("CAMERA_PERMISSION_DENIED")
            }
        }
        // 허가/거부 어느 쪽이든 화면(단계 상태 포함)을 다시 계산한다.
        updateScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

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

        // 화면2 "시작": 카메라·마이크 켜기. 권한·보정이 이미 끝났으므로 서비스만 켠다
        // (저장된 프로파일이 있어 runCalibrationIfNeeded가 22초 보정을 건너뛴다).
        binding.btnStart.setOnClickListener {
            startControllerAndCalibrate()
        }
        // "얼굴 인식 설정 다시 하기" = 보정을 처음부터 다시 잡는다. 보정엔 카메라가 필요하므로
        // 서비스가 꺼져 있으면 먼저 켠다(서비스가 뜨면 예약된 보정이 실행된다).
        // 프로파일을 미리 지우지 않는 이유: 지우면 setupComplete가 잠깐 false가 돼 설정 화면으로 튄다.
        binding.btnRecalibrate.setOnClickListener {
            startOverlayService()
            ControllerPipeline.runCalibration()
        }
        // 정지 = 카메라·마이크 끄기. 서비스가 죽으면 파이프라인도 같이 정리된다.
        binding.btnStop.setOnClickListener {
            OverlayService.stop(this)
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
        // Firebase 업로드 테스트는 개발용이라 디버그 빌드에서만 노출한다.
        // (로컬 Logcat 업로드 테스트 버튼은 제거했다.)
        if (BuildConfig.DEBUG) {
            binding.btnUploadFirebaseTest.setOnClickListener {
                TelemetryUploadWorker.enqueueManualTestUpload(
                    context = applicationContext,
                    useLogcatUploader = false,
                )
                binding.telemetryStatusText.setText(R.string.telemetry_firebase_upload_test_started)
            }
        } else {
            binding.btnUploadFirebaseTest.visibility = View.GONE
        }
        updateTelemetryStatus()
        updateScreen()

        lifecycleScope.launch {
            ControllerPipeline.calibrating.collect { calibrating ->
                // 초기 설정(3단계)에서 시작한 보정이 끝나면 서비스를 내려 화면2를 "대기" 상태로 둔다.
                // 이렇게 해야 화면2로 넘어올 때 자동으로 켜지지 않고, 사용자가 "시작"을 눌러야 켜진다.
                if (!calibrating && stopAfterInitialCalibration && calibrationDone()) {
                    stopAfterInitialCalibration = false
                    OverlayService.stop(this@ControllerActivity)
                }
                // 초기 보정이 끝나 프로파일이 저장되면 setupComplete=true가 되어 메인 화면으로 넘어간다.
                updateScreen()
            }
        }
    }

    private fun applySystemBarInsets() {
        val initialBottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottomPadding + systemBars.bottom,
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // 접근성/오버레이/카메라·마이크 권한은 설정 화면에서 바뀌고 돌아오므로,
        // 돌아올 때마다 다시 확인한다. 하나라도 꺼졌으면 설정 화면으로 되돌린다.
        updateScreen()
    }

    /**
     * 설정 완료 여부에 따라 초기 설정 화면 ↔ 메인 화면을 전환한다.
     *
     * 메인 화면에는 상태 표시를 두지 않는다 — 서비스 start/stop이 비동기라 버튼 직후
     * 실행 여부를 정확히 알 수 없었고(옛 값이 찍힘), 어차피 포인터 오버레이와 시작/정지
     * 버튼으로 상태가 드러나 중복이었다.
     */
    private fun updateScreen() {
        if (setupComplete()) {
            binding.screenSetup.visibility = View.GONE
            binding.screenMain.visibility = View.VISIBLE
        } else {
            binding.screenMain.visibility = View.GONE
            binding.screenSetup.visibility = View.VISIBLE
            refreshStepStates()
        }
    }

    /** 세 단계(접근성·오버레이·카메라마이크+초기보정)가 모두 끝났는지. 메인 화면 진입 조건. */
    private fun setupComplete(): Boolean =
        isAccessibilityServiceEnabled() && Settings.canDrawOverlays(this) &&
            cameraMicGranted() && calibrationDone()

    private fun cameraMicGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun calibrationDone(): Boolean = CalibrationStore.load(this) != null

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
        // 3단계 "초기 얼굴인식 설정" = 카메라·마이크 허가 + 초기 캘리브레이션 완료
        val startDone = cameraMicGranted() && calibrationDone()

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
