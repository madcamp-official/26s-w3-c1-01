package com.mobileconductor.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.orchestrator.calibration.CalibrationUiState

/**
 * 오버레이 렌더러 (FR-008). Canvas로 포인터/상태 인디케이터/클릭 피드백/캘리브레이션 기준점/
 * 수동 해제 버튼을 그린다. 상태별 시각 규칙은 [OverlayVisuals]에서 받아 사용한다.
 *
 * 데이터는 [OverlayService]가 setter로 밀어넣고, 값이 바뀌면 invalidate한다.
 */
class OverlayView(context: Context) : View(context) {

    private var state: ControllerState = ControllerState.CALIBRATING
    private var visuals: OverlayVisuals = OverlayVisuals.forState(state)
    private var pointer: PointerFrame? = null
    private var calibration: CalibrationUiState? = null

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

    private val unlockRect = RectF()
    private var clickX = 0f
    private var clickY = 0f
    private var clickProgress = 0f
    private var clickAnimator: ValueAnimator? = null

    fun setState(state: ControllerState) {
        this.state = state
        this.visuals = OverlayVisuals.forState(state)
        invalidate()
    }

    fun setPointer(frame: PointerFrame) {
        this.pointer = frame
        invalidate()
    }

    fun setCalibration(ui: CalibrationUiState?) {
        this.calibration = ui
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

        drawIndicator(canvas, w)
        drawClickRipple(canvas, w, h)

        if (visuals.showManualUnlock) drawUnlockButton(canvas, w, h) else unlockRect.setEmpty()
    }

    private fun drawPointer(canvas: Canvas, w: Float, h: Float) {
        val p = pointer ?: return
        val cx = p.x * w
        val cy = p.y * h
        val radius = dp(14f)
        when (visuals.pointerVisibility) {
            PointerVisibility.MOVING -> {
                fill.color = colorFor(visuals.indicatorColor); fill.alpha = 255
                canvas.drawCircle(cx, cy, radius, fill)
            }
            PointerVisibility.FIXED_TRANSLUCENT -> {
                fill.color = colorFor(visuals.indicatorColor); fill.alpha = 110
                canvas.drawCircle(cx, cy, radius, fill)
            }
            PointerVisibility.MOVING_HIGHLIGHT -> {
                fill.color = colorFor(visuals.indicatorColor); fill.alpha = 255
                canvas.drawCircle(cx, cy, radius, fill)
                stroke.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius + dp(6f), stroke)
            }
            PointerVisibility.HIDDEN -> { /* LOCKED: 포인터 미표시 */ }
            PointerVisibility.CALIBRATION_ONLY -> { /* CALIBRATING 분기에서 처리 */ }
        }
    }

    private fun drawCalibration(canvas: Canvas, w: Float, h: Float) {
        val ui = calibration ?: return
        // 현재 기준점 하이라이트
        val cx = ui.currentPoint.screenX * w
        val cy = ui.currentPoint.screenY * h
        fill.color = colorFor(IndicatorColor.BLUE); fill.alpha = 255
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

    private fun drawIndicator(canvas: Canvas, w: Float) {
        val cx = w / 2f
        val cy = dp(28f)
        fill.color = colorFor(visuals.indicatorColor); fill.alpha = 255
        canvas.drawCircle(cx - dp(40f), cy, dp(8f), fill)
        val label = visuals.label
        if (label != null) {
            canvas.drawText(label, cx - dp(26f), cy + dp(5f), text)
        }
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

    private fun drawUnlockButton(canvas: Canvas, w: Float, h: Float) {
        val bw = dp(180f)
        val bh = dp(56f)
        val left = (w - bw) / 2f
        val top = h - bh - dp(40f)
        unlockRect.set(left, top, left + bw, top + bh)
        fill.color = Color.parseColor("#455A64"); fill.alpha = 235
        canvas.drawRoundRect(unlockRect, dp(12f), dp(12f), fill)
        canvas.drawText("잠금 해제", unlockRect.left + dp(48f), unlockRect.centerY() + dp(5f), text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 창은 LOCKED에서만 터치 가능(서비스가 플래그 토글). 버튼 영역 탭 시 해제 액션.
        if (event.action == MotionEvent.ACTION_UP &&
            visuals.showManualUnlock &&
            unlockRect.contains(event.x, event.y)
        ) {
            OverlayBus.onManualUnlock?.invoke()
            return true
        }
        return false
    }

    private fun colorFor(c: IndicatorColor): Int = when (c) {
        IndicatorColor.GREEN -> Color.parseColor("#4CAF50")
        IndicatorColor.YELLOW -> Color.parseColor("#FBC02D")
        IndicatorColor.GRAY -> Color.parseColor("#9E9E9E")
        IndicatorColor.BLUE -> Color.parseColor("#2196F3")
        IndicatorColor.PROGRESS -> Color.parseColor("#4CAF50")
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
