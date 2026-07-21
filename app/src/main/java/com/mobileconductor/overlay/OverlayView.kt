package com.mobileconductor.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.orchestrator.calibration.CalibrationUiState

/**
 * 오버레이 렌더러 (FR-008). Canvas로 포인터/클릭 피드백/캘리브레이션 기준점을 그린다.
 * 상태별 시각 규칙은 [OverlayVisuals]에서 받아 사용한다.
 *
 * **터치를 받지 않는다** — 창이 항상 FLAG_NOT_TOUCHABLE이라 순수 그리기 표면이다.
 * 수동 해제 버튼은 [OverlayService]가 별도 창으로 띄운다(잠금 중 직접 조작을 막지 않기 위해).
 *
 * 데이터는 [OverlayService]가 setter로 밀어넣고, 값이 바뀌면 invalidate한다.
 */
class OverlayView(context: Context) : View(context) {

    private var state: ControllerState = ControllerState.CALIBRATING
    private var visuals: OverlayVisuals = OverlayVisuals.forState(state)
    private var pointer: PointerFrame? = null
    private var calibration: CalibrationUiState? = null

    /** 얼굴 미검출 여부. 상태(ControllerState)와는 별개의 신호라 따로 들고 있는다. */
    private var faceLost: Boolean = false

    /** 음성 인식기가 지금 발화를 캡처하는 구간인지. 죽은 구간("띹" 이후)이면 포인터를 회색으로 만든다. */
    private var listening: Boolean = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14f)
        typeface = Typeface.DEFAULT_BOLD
    }

    // 입력 불가(얼굴 미검출 등) 시 포인터 링 색. 상단 인디케이터 회색과 같은 톤.
    private val notReadyGray = Color.parseColor("#9E9E9E")

    private var clickX = 0f
    private var clickY = 0f
    private var clickProgress = 0f
    private var clickAnimator: ValueAnimator? = null

    fun setState(state: ControllerState) {
        this.state = state
        this.visuals = OverlayVisuals.forState(state)
        invalidate()
    }

    /**
     * 얼굴을 놓친 프레임은 **좌표를 갱신하지 않고 마지막 위치를 유지한다.**
     *
     * A는 미검출 시 (0.5, 0.5)를 보낸다 — null을 안 보내려는 결정이라(OPEN_ISSUES #3)
     * 좌표 자체는 의미가 없다. 그대로 그리면 얼굴을 가릴 때마다 포인터가 화면 중앙으로
     * 순간이동해서 **사용자가 조작하던 위치를 잃는다.** 마지막 위치를 누가 들고 있을지
     * A와 D 사이에 정해진 적이 없어 통합에서 소비 측(D)이 맡기로 했다.
     *
     * 얼굴 미검출은 에러가 아니라 안내 대상이다(FR-001) — [faceLost]로 표시만 한다.
     */
    fun setPointer(frame: PointerFrame) {
        faceLost = !frame.faceDetected
        if (frame.faceDetected) this.pointer = frame
        invalidate()
    }

    fun setCalibration(ui: CalibrationUiState?) {
        this.calibration = ui
        invalidate()
    }

    /** 음성 청취 구간 변화. 자주 토글되므로 값이 바뀔 때만 다시 그린다. */
    fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        invalidate()
    }

    /** 클릭 성공 피드백: 좌표(정규화 0~1)에서 짧은 리플 애니메이션. */
    fun triggerClick(nx: Float, ny: Float) {
        clickAnimator?.cancel()
        clickX = nx
        clickY = ny
        clickAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            addUpdateListener {
                clickProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (state == ControllerState.CALIBRATING) {
            drawCalibration(canvas, w, h)
        } else {
            drawPointer(canvas, w, h)
        }

        drawClickRipple(canvas, w, h)
    }

    private fun drawPointer(canvas: Canvas, w: Float, h: Float) {
        val p = pointer ?: return
        // LOCKED(숨김)·CALIBRATING(전용 UI)에서는 포인터를 그리지 않는다.
        if (visuals.pointerVisibility == PointerVisibility.HIDDEN ||
            visuals.pointerVisibility == PointerVisibility.CALIBRATION_ONLY
        ) return

        val cx = p.x * w
        val cy = p.y * h
        val radius = dp(12f)

        // 속이 빈 링으로 그린다 — 채워진 원보다 화면을 덜 가린다.
        // 색으로 입력 가능 여부를 알린다:
        //   흰색 = 지금 입력을 받는다(ACTIVE + 얼굴 추적 정상 + 음성 청취 구간).
        //   회색 = 입력이 의미 없는 순간 — 얼굴을 놓쳤거나, 음성 인식기가 "띵" 이후 죽은 구간이라
        //          말해도 안 잡힐 때. 사용자가 "지금은 안 되는구나"를 눈으로 알 수 있게 한다.
        val ready = state == ControllerState.ACTIVE && !faceLost && listening
        val ringColor = if (ready) Color.WHITE else notReadyGray
        // 흰 링이 흰 배경에서 사라지지 않도록 옅은 어두운 테두리를 먼저 깐다(가림은 최소, 대비는 확보).
        stroke.color = Color.BLACK
        stroke.alpha = 70
        stroke.strokeWidth = dp(5f)
        canvas.drawCircle(cx, cy, radius, stroke)

        stroke.color = ringColor
        stroke.alpha = 255
        stroke.strokeWidth = dp(3f)
        canvas.drawCircle(cx, cy, radius, stroke)

        // 공유 stroke Paint 상태 복원(클릭 리플 등 다른 그리기와 간섭 방지).
        stroke.strokeWidth = dp(3f)
        stroke.alpha = 255
    }

    private fun drawCalibration(canvas: Canvas, w: Float, h: Float) {
        val ui = calibration ?: return
        // 현재 기준점 하이라이트
        val cx = ui.currentPoint.screenX * w
        val cy = ui.currentPoint.screenY * h
        fill.color = colorFor(IndicatorColor.PROGRESS); fill.alpha = 255
        canvas.drawCircle(cx, cy, dp(18f), fill)
        stroke.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(26f), stroke)

        // 하단 진행률 바
        val margin = dp(24f)
        val barTop = h - dp(48f)
        val barBottom = barTop + dp(10f)
        fill.color = Color.DKGRAY; fill.alpha = 180
        canvas.drawRoundRect(margin, barTop, w - margin, barBottom, dp(5f), dp(5f), fill)
        fill.color = colorFor(IndicatorColor.GREEN); fill.alpha = 255
        val filledRight = margin + (w - 2 * margin) * ui.progress
        canvas.drawRoundRect(margin, barTop, filledRight, barBottom, dp(5f), dp(5f), fill)
        canvas.drawText("보정 ${(ui.progress * 100).toInt()}%", margin, barTop - dp(8f), text)
    }

    private fun drawClickRipple(canvas: Canvas, w: Float, h: Float) {
        if (clickProgress <= 0f || clickProgress >= 1f) return
        val cx = clickX * w
        val cy = clickY * h
        val radius = dp(16f) + dp(50f) * clickProgress
        val alpha = (255 * (1f - clickProgress)).toInt().coerceIn(0, 255)

        // 어떤 배경 위에서도 눈에 띄도록 굵은 주황색 링을 그림
        stroke.color = Color.parseColor("#FF6D00")
        stroke.alpha = alpha
        stroke.strokeWidth = dp(8f)
        canvas.drawCircle(cx, cy, radius, stroke)

        stroke.strokeWidth = dp(3f)
        stroke.alpha = 255
    }

    private fun colorFor(c: IndicatorColor): Int = when (c) {
        IndicatorColor.GREEN -> Color.parseColor("#4CAF50")
        IndicatorColor.GRAY -> Color.parseColor("#9E9E9E")
        IndicatorColor.PROGRESS -> Color.parseColor("#4CAF50")
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
