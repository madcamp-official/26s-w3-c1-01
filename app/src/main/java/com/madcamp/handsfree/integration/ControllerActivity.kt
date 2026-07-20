package com.madcamp.handsfree.integration

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.R
import com.madcamp.handsfree.tracking.FaceTracker
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.orchestrator.ConductorContainer
import com.mobileconductor.overlay.ClickFeedback
import com.mobileconductor.overlay.OverlayBus
import com.mobileconductor.overlay.OverlayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 통합 진입점. A(카메라) · B(음성) · C(터치 주입) · D(상태/오버레이)를 한 화면에서 조립한다.
 *
 * D의 `DebugActivity`(Compose)와 A의 `TrackerDebugActivity`를 대체한다. 이 화면 자체는
 * 권한 확보와 캘리브레이션 시작만 담당하고, **실제 조작 UI는 오버레이(D)** 다 —
 * 다른 앱 위에서 쓰는 게 이 앱의 목적이라 화면에 남아 있을 이유가 없다.
 *
 * 시작 순서가 중요하다: 권한 → 접근성 서비스 → 오버레이 → 트래커 → 캘리브레이션 → ACTIVE.
 * **캘리브레이션 없이 ACTIVE로 보내면 안 된다(FR-006)** — 보정 전에는 포인터가 화면
 * 구석에 처박혀서 앱이 고장 난 것처럼 보인다.
 */
class ControllerActivity : AppCompatActivity() {

    private lateinit var tracker: FaceTracker
    private lateinit var deps: RealConductorDependencies
    private lateinit var container: ConductorContainer

    private lateinit var statusText: TextView

    /** 진행 중인 캘리브레이션. 재보정 시 이전 것을 취소하는 데 쓴다. */
    private var calibrationJob: Job? = null

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) startPipeline() else statusText.text = getString(R.string.status_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        statusText = findViewById(R.id.status_text)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.start(this)
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                )
            }
        }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun startPipeline() {
        // 파이프라인은 한 번만 세운다(카메라/오케스트레이터 이중 기동 방지).
        // 다만 캘리브레이션은 다시 돌 수 있어야 한다 — 보정이 어긋났을 때
        // 앱을 죽였다 켜는 것 말고 방법이 없으면 실기기에서 손을 못 댄다.
        if (::container.isInitialized) {
            runCalibration()
            return
        }

        tracker = FaceTracker(this)
        deps = RealConductorDependencies(applicationContext, lifecycleScope, tracker)
        container = ConductorContainer(
            deps = deps,
            scope = lifecycleScope,
            // 캘리브레이션이 끝나기 전에는 명령을 받지 않는다(FR-006)
            initialState = ControllerState.CALIBRATING,
        )

        tracker.displayRotationDegrees = currentRotationDegrees()
        tracker.start(this)
        deps.voice.start()

        wireOverlay()
        runCalibration()
    }

    private fun wireOverlay() {
        val orchestrator = container.orchestrator

        // 음성 인식이 반복 실패해도 앱에서 빠져나올 수 있어야 한다(FR-005)
        OverlayBus.onManualUnlock = { deps.voice.inject(CommandId.UNLOCK) }

        lifecycleScope.launch { orchestrator.state.collect { OverlayBus.publishState(it) } }
        lifecycleScope.launch { orchestrator.pointerFrames.collect { OverlayBus.publishPointer(it) } }
        lifecycleScope.launch {
            orchestrator.executionResults.collect { result ->
                if (!result.success) return@collect
                // 좌표 없는 명령(BACK 등)은 클릭 애니메이션을 띄우지 않는다
                val x = result.x ?: return@collect
                val y = result.y ?: return@collect
                OverlayBus.publishClick(ClickFeedback(x, y))
            }
        }
        lifecycleScope.launch {
            container.calibrationController.uiState.collect { OverlayBus.publishCalibration(it) }
        }
    }

    private fun runCalibration() {
        // 진행 중에 버튼을 또 누르면 두 개의 수집 루프가 같은 좌표 스트림을 나눠 갖게 되고
        // 양쪽 다 표본이 모자라 실패한다. 이전 실행을 취소하고 새로 시작한다.
        calibrationJob?.cancel()
        calibrationJob = lifecycleScope.launch {
            statusText.setText(R.string.status_calibrating)
            container.calibrationController.run(profileId = "default")
            // 프로파일이 A에 주입된 뒤에야 ACTIVE로 보낸다.
            // CALIBRATING에서는 모든 음성 명령이 폐기되므로 명령 주입으로는 못 나간다.
            // 재보정일 때는 이미 ACTIVE라 이 호출이 무시된다(그대로 두는 게 맞다).
            container.orchestrator.onCalibrationComplete()
            OverlayBus.publishCalibration(null)
            statusText.setText(R.string.status_active)
        }
    }

    /**
     * 회전 보정은 A가 흡수한다. 여기서 각도를 알려주기만 하면 C/D는 가로/세로를
     * 신경 쓸 필요가 없다(OPEN_ISSUES #5).
     */
    private fun currentRotationDegrees(): Int {
        // Activity.getDisplay()는 API 30부터다. minSdk가 26이라 하위에서는
        // deprecated된 defaultDisplay로 떨어진다 — 실기기 검증도 API 26에서 했다.
        val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
        if (::tracker.isInitialized) tracker.displayRotationDegrees = currentRotationDegrees()
    }

    override fun onDestroy() {
        if (::tracker.isInitialized) tracker.stop()
        if (::deps.isInitialized) deps.voice.stop()
        if (::container.isInitialized) container.orchestrator.stop()
        OverlayBus.onManualUnlock = null
        super.onDestroy()
    }
}
