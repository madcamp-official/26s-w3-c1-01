package com.madcamp.handsfree.integration

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.BuildConfig
import com.madcamp.handsfree.R
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

    private lateinit var statusText: TextView
    private lateinit var telemetryStatusText: TextView
    private lateinit var telemetrySettings: TelemetrySettings
    private lateinit var telemetryQueue: LocalTelemetryQueue

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
            statusText.setText(R.string.status_permission_denied)
            updateTelemetryStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        TelemetryCrashHandler.install(applicationContext)
        TelemetryUploadWorker.scheduleDailyAt9(applicationContext)

        telemetrySettings = TelemetrySettings(applicationContext)
        telemetryQueue = LocalTelemetryQueue(applicationContext)
        statusText = findViewById(R.id.status_text)
        telemetryStatusText = findViewById(R.id.telemetry_status_text)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
        // 저장된 프로파일을 버리고 처음부터 다시 잡는다.
        // 자세나 거치 위치가 바뀌면 기존 범위가 안 맞는다.
        findViewById<Button>(R.id.btn_recalibrate).setOnClickListener {
            CalibrationStore.clear(this)
            ControllerPipeline.runCalibration()
        }
        // 알림의 정지 버튼과 같은 동작. 서비스가 죽으면 파이프라인도 같이 정리된다.
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            OverlayService.stop(this)
            statusText.setText(R.string.status_idle)
        }

        val telemetryLogger = Telemetry.logger(applicationContext)
        val telemetryConsent = findViewById<CheckBox>(R.id.checkbox_telemetry_consent)
        telemetryConsent.isChecked = telemetrySettings.diagnosticsEnabled
        telemetryConsent.setOnCheckedChangeListener { _, isChecked ->
            telemetrySettings.diagnosticsEnabled = isChecked
            if (isChecked) {
                telemetryLogger.logAppOpened()
            }
            updateTelemetryStatus()
        }
        telemetryLogger.logAppOpened()

        val feedbackMessage = findViewById<EditText>(R.id.feedback_message)
        val feedbackSituation = findViewById<EditText>(R.id.feedback_situation)
        findViewById<Button>(R.id.btn_send_feedback).setOnClickListener {
            val message = feedbackMessage.text.toString().trim()
            val situation = feedbackSituation.text.toString().trim()
            if (message.isBlank() && situation.isBlank()) {
                telemetryStatusText.setText(R.string.telemetry_feedback_empty)
                return@setOnClickListener
            }
            telemetryLogger.logUserFeedback(message, situation)
            feedbackMessage.text.clear()
            feedbackSituation.text.clear()
            telemetryStatusText.text = getString(
                R.string.telemetry_feedback_saved,
                telemetryQueue.count(),
            )
        }
        // 업로드 테스트 버튼은 개발용이다. 낯선 사용자에게 배포하는 화면에
        // "Logcat", "Firebase" 같은 말이 보이면 앱이 미완성으로 읽힌다.
        // 기능을 지우지 않고 숨기기만 하는 이유는 디버그 빌드에서 계속 쓰기 때문이다.
        val uploadTestButton = findViewById<Button>(R.id.btn_upload_test)
        val firebaseTestButton = findViewById<Button>(R.id.btn_upload_firebase_test)
        if (BuildConfig.DEBUG) {
            uploadTestButton.setOnClickListener {
                TelemetryUploadWorker.enqueueManualTestUpload(
                    context = applicationContext,
                    useLogcatUploader = true,
                )
                telemetryStatusText.setText(R.string.telemetry_upload_test_started)
            }
            firebaseTestButton.setOnClickListener {
                TelemetryUploadWorker.enqueueManualTestUpload(
                    context = applicationContext,
                    useLogcatUploader = false,
                )
                telemetryStatusText.setText(R.string.telemetry_firebase_upload_test_started)
            }
        } else {
            uploadTestButton.visibility = View.GONE
            firebaseTestButton.visibility = View.GONE
        }
        updateTelemetryStatus()

        lifecycleScope.launch {
            ControllerPipeline.calibrating.collect { running ->
                statusText.setText(
                    when {
                        running -> R.string.status_calibrating
                        ControllerPipeline.isRunning -> R.string.status_active
                        else -> R.string.status_idle
                    }
                )
            }
        }
    }

    private fun updateTelemetryStatus() {
        telemetryStatusText.text = if (telemetrySettings.diagnosticsEnabled) {
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
            statusText.setText(R.string.status_need_overlay)
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
