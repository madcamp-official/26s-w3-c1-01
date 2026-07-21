package com.mobileconductor.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
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
 * **전체 오버레이 창은 항상 터치 통과(FLAG_NOT_TOUCHABLE)다** — 잠금 중에도 사용자가 아래 앱을
 * 손으로 자유롭게 조작할 수 있어야 하기 때문. 예전엔 LOCKED에서 이 창을 통째로 터치 가능하게
 * 만들어 해제 버튼을 받았는데, 그러면 창이 모든 터치를 삼켜 직접 조작이 막혔다. 그래서 해제
 * 버튼만 [unlockView]라는 별도의 작은 터치 창으로 분리했다(FLAG_NOT_TOUCH_MODAL로 버튼 밖
 * 터치는 뒤로 통과). 이 버튼 창은 LOCKED에서만 붙인다.
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var added = false

    /** LOCKED에서만 붙는 "잠금 해제" 버튼 전용 창. 전체 오버레이와 분리해 나머지 터치는 통과시킨다. */
    private lateinit var unlockView: View
    private lateinit var unlockParams: WindowManager.LayoutParams
    private var unlockAdded = false


    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)
        layoutParams = buildOverlayParams()
        unlockView = buildUnlockButton()
        unlockParams = buildUnlockParams()

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
                updateUnlockButton(state)
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
        lifecycleScope.launch {
            OverlayBus.listening.collect { overlayView.setListening(it) }
        }
    }

    /**
     * LOCKED에서만 "잠금 해제" 버튼 창을 붙이고, 벗어나면 뗀다.
     * 상태별 노출 여부는 [OverlayVisuals]의 showManualUnlock을 SSOT로 따른다.
     */
    private fun updateUnlockButton(state: ControllerState) {
        if (!canDrawOverlays()) return
        val shouldShow = OverlayVisuals.forState(state).showManualUnlock
        if (shouldShow && !unlockAdded) {
            windowManager.addView(unlockView, unlockParams)
            unlockAdded = true
        } else if (!shouldShow && unlockAdded) {
            windowManager.removeView(unlockView)
            unlockAdded = false
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** 전체 오버레이 창. 항상 터치 통과 — 잠금 중에도 아래 앱을 손으로 조작할 수 있어야 한다. */
    private fun buildOverlayParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }

    /**
     * 해제 버튼 창. 버튼 크기(WRAP_CONTENT)만큼만 차지하고 터치를 받는다.
     * FLAG_NOT_TOUCH_MODAL로 버튼 밖 터치는 뒤 창(=통과되는 전체 오버레이)으로 넘겨 앱에 닿게 한다.
     */
    private fun buildUnlockParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(40)
        }
    }

    /** "잠금 해제" 버튼(둥근 어두운 배경 + 흰 글자, 가운데 정렬). 탭하면 해제 액션을 부른다. */
    private fun buildUnlockButton(): View =
        TextView(this).apply {
            text = "잠금 해제"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(Color.argb(235, 0x45, 0x5A, 0x64))
            }
            setPadding(dp(44), dp(16), dp(44), dp(16))
            isClickable = true
            isFocusable = false
            setOnClickListener { OverlayBus.onManualUnlock?.invoke() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        if (unlockAdded) {
            windowManager.removeView(unlockView)
            unlockAdded = false
        }
        if (added) {
            windowManager.removeView(overlayView)
            added = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HF-Overlay"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.madcamp.handsfree.STOP_CONTROLLER"

        /**
         * 정지가 사용자 요청이었는지. onDestroy에서 원인을 구분하는 데만 쓴다.
         * 앱 버튼([stop])과 알림 버튼(ACTION_STOP) 두 경로가 모두 여기를 세운다.
         */
        @Volatile
        private var stoppedByUser = false

        fun start(context: Context) {
            stoppedByUser = false
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            // stopService()는 onStartCommand를 거치지 않아서 서비스 안에서는
            // 사용자가 껐는지 알 수 없다. 여기서 표시해 두지 않으면 onDestroy가
            // 정상 종료를 "시스템이 죽였다"로 잘못 보고한다(실제로 그래서 삼성
            // 배터리 정책을 한참 의심했다).
            stoppedByUser = true
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
