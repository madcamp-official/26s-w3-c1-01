package com.madcamp.handsfree.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.madcamp.handsfree.contract.PointerFrame

/**
 * A파트 **검증 전용** 포인터 표시.
 *
 * 오버레이는 원래 D 담당이다. 이걸 만든 이유는 "감도를 바꿨더니 체감상 달라진다"가
 * A의 완료 기준(DoD)인데, 로그 숫자만 봐서는 판단할 수 없기 때문이다.
 * **D의 오버레이를 대신 만드는 게 아니다 — 통합 시 이 클래스는 버린다.**
 */
class PointerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var frame: PointerFrame? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun update(newFrame: PointerFrame) {
        frame = newFrame
        // 분석 스레드에서 호출되므로 invalidate()를 직접 부르면 안 된다
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 3등분 격자. 포인터가 화면 끝까지 닿는지 눈으로 확인하는 용도다
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..2) {
            canvas.drawLine(w * i / 3f, 0f, w * i / 3f, h, gridPaint)
            canvas.drawLine(0f, h * i / 3f, w, h * i / 3f, gridPaint)
        }

        val f = frame ?: return
        val cx = f.x * w
        val cy = f.y * h

        // 얼굴을 놓쳤을 때 회색으로 바꿔서, 좌표가 멈춘 건지 앱이 멈춘 건지 구분한다
        dotPaint.color = if (f.faceDetected) {
            Color.parseColor("#4CAF50")
        } else {
            Color.parseColor("#9E9E9E")
        }

        canvas.drawCircle(cx, cy, 22f, dotPaint)
        canvas.drawCircle(cx, cy, 22f, ringPaint)
    }
}
