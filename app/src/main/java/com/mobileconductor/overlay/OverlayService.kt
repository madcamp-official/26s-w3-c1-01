package com.mobileconductor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.integration.ControllerPipeline
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

    /** 정지가 사용자 요청이었는지. onDestroy에서 원인을 구분하는 데만 쓴다 */
    private var stoppedByUser = false

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

        // 통합 시 추가. A/B/C/D 파이프라인의 수명을 이 서비스에 맞춘다.
        // Activity가 들고 있으면 CameraX가 Activity 라이프사이클에 묶여서 앱을 나가는
        // 순간 카메라가 끊긴다 — 다른 앱 위에서 쓰는 앱이라 그러면 성립하지 않는다.
        ControllerPipeline.start(this)
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

    /**
     * 알림의 정지 버튼에서 들어오는 요청을 처리한다.
     *
     * 포그라운드 서비스 알림은 밀어서 지울 수 없고 앱을 나가서 쓰는 게 목적이라,
     * **알림에 정지 수단이 없으면 강제 중지 말고는 끌 방법이 없다.**
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "정지 요청 수신 (사용자가 정지 버튼을 눌렀다)")
            stoppedByUser = true
            stopSelf()
            return START_NOT_STICKY
        }
        Log.i(TAG, "서비스 시작 (intent=${intent?.action ?: "없음"}, flags=$flags)")
        return START_STICKY
    }

    /**
     * 최근 앱 목록에서 밀어 없앴을 때. **여기서 서비스를 멈추지 않는다.**
     *
     * 다른 앱 위에서 쓰는 게 이 앱의 목적이라 화면을 치웠다고 컨트롤러가 죽으면 안 된다.
     * 다만 일부 제조사(특히 삼성)는 이 콜백 뒤에 프로세스를 강제로 정리한다 —
     * 그 경우 로그로 구분할 수 있어야 원인을 찾는다.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "최근 앱에서 제거됨 — 서비스는 계속 유지한다")
        super.onTaskRemoved(rootIntent)
    }

    private fun startAsForeground() {
        val channelId = "overlay_controller"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Controller Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("핸즈프리 컨트롤러")
            .setContentText("카메라·음성으로 조작 중")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "정지",
                    stopIntent,
                ).build()
            )
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        // 사용자가 껐는지, 시스템/제조사가 죽였는지 구분해야 원인을 좁힐 수 있다
        if (stoppedByUser) {
            Log.i(TAG, "서비스 종료 — 사용자 요청")
        } else {
            Log.w(TAG, "서비스 종료 — 요청하지 않았는데 죽었다(시스템 또는 제조사 배터리 정책)")
        }
        ControllerPipeline.stop()
        if (added) {
            windowManager.removeView(overlayView)
            added = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.madcamp.handsfree.STOP_CONTROLLER"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
