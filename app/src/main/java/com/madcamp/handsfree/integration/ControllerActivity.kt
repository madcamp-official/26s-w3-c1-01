package com.madcamp.handsfree.integration

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.R
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

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) startControllerAndCalibrate()
        else statusText.setText(R.string.status_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller)

        statusText = findViewById(R.id.status_text)

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
        // 알림의 정지 버튼과 같은 동작. 서비스가 죽으면 파이프라인도 같이 정리된다.
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            OverlayService.stop(this)
            statusText.setText(R.string.status_idle)
        }

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
        ControllerPipeline.runCalibration()
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
