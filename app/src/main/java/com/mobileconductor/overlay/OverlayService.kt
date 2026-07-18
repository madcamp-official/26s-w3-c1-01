package com.mobileconductor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mobileconductor.core.model.ControllerState
import kotlinx.coroutines.launch

/**
 * 오버레이 표시 포그라운드 서비스 (FR-008).
 *
 * [OverlayView]를 WindowManager의 시스템 오버레이(TYPE_APPLICATION_OVERLAY)로 띄우고,
 * [OverlayBus]를 구독해 상태/포인터/캘리브레이션/클릭 피드백을 렌더에 반영한다.
 *
 * 창은 기본적으로 터치 통과(FLAG_NOT_TOUCHABLE)이며, LOCKED 상태에서만 터치를 받아
 * 수동 잠금 해제 버튼이 동작하도록 플래그를 토글한다.
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var added = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)
        layoutParams = buildLayoutParams(touchable = false)

        if (canDrawOverlays()) {
            windowManager.addView(overlayView, layoutParams)
            added = true
        }

        observeBus()
    }

    private fun observeBus() {
        lifecycleScope.launch {
            OverlayBus.state.collect { state ->
                overlayView.setState(state)
                updateTouchability(state)
            }
        }
        lifecycleScope.launch {
            OverlayBus.pointer.collect { frame -> frame?.let { overlayView.setPointer(it) } }
        }
        lifecycleScope.launch {
            OverlayBus.calibration.collect { overlayView.setCalibration(it) }
        }
        lifecycleScope.launch {
            OverlayBus.clicks.collect { overlayView.triggerClick(it.x, it.y) }
        }
    }

    /** LOCKED에서만 창을 터치 가능하게 하여 해제 버튼이 이벤트를 받도록 한다. */
    private fun updateTouchability(state: ControllerState) {
        if (!added) return
        val touchable = state == ControllerState.LOCKED
        val newParams = buildLayoutParams(touchable)
        layoutParams = newParams
        windowManager.updateViewLayout(overlayView, layoutParams)
    }

    private fun buildLayoutParams(touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    private fun startAsForeground() {
        val channelId = "overlay_controller"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Controller Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Mobile Conductor")
            .setContentText("핸즈프리 컨트롤러 오버레이 실행 중")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        if (added) {
            windowManager.removeView(overlayView)
            added = false
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
