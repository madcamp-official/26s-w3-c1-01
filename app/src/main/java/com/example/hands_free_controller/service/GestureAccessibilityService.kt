package com.example.hands_free_controller.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class GestureAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GestureAccessibilityService? = null
            private set

        private const val HORIZONTAL_CURVE_RATIO = 0.025f
        private const val VERTICAL_CURVE_RATIO = 0.015f
        private const val FLING_BREAKPOINT_RATIO = 0.58f
    }

    private var activeDragStroke: GestureDescription.StrokeDescription? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 지금 단계에서는 사용하지 않음
    }

    override fun onInterrupt() {
        // 지금 단계에서는 사용하지 않음
    }

    fun back(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun tap(x: Float, y: Float, onResult: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(x, y)
        }

        dispatchPath(path, 80, onResult)
    }

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320,
        onResult: (Boolean) -> Unit
    ) {
        dispatchPath(curvedPath(startX, startY, endX, endY), durationMs, onResult)
    }

    fun flingSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        warmupDurationMs: Long,
        flingDurationMs: Long,
        onResult: (Boolean) -> Unit
    ) {
        val midX = startX + (endX - startX) * FLING_BREAKPOINT_RATIO
        val midY = startY + (endY - startY) * FLING_BREAKPOINT_RATIO
        val firstPath = curvedPath(startX, startY, midX, midY)
        val firstStroke = GestureDescription.StrokeDescription(firstPath, 0, warmupDurationMs, true)

        dispatchStroke(
            stroke = firstStroke,
            onCompleted = {
                val secondPath = curvedPath(midX, midY, endX, endY)
                val secondStroke = firstStroke.continueStroke(secondPath, 0, flingDurationMs, false)
                dispatchStroke(
                    stroke = secondStroke,
                    onCompleted = { onResult(true) },
                    onCancelled = { onResult(false) },
                )
            },
            onCancelled = { onResult(false) },
        )
    }

    fun drag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 600,
        onResult: (Boolean) -> Unit
    ) {
        swipe(startX, startY, endX, endY, durationMs, onResult)
    }

    fun startDrag(x: Float, y: Float, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(true)
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        activeDragStroke = GestureDescription.StrokeDescription(path, 0, 1, true)
        dispatchStroke(activeDragStroke!!, onResult)
    }

    fun continueDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(true)
            return
        }

        val currentStroke = activeDragStroke
        if (currentStroke == null) {
            onResult(false)
            return
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        activeDragStroke = currentStroke.continueStroke(path, 0, 80, true)
        dispatchStroke(activeDragStroke!!, onResult)
    }

    fun endDrag(
        x: Float,
        y: Float,
        onResult: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(true)
            return
        }

        val currentStroke = activeDragStroke
        if (currentStroke == null) {
            onResult(false)
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        activeDragStroke = null
        dispatchStroke(currentStroke.continueStroke(path, 0, 1, false), onResult)
    }

    fun cancelDrag() {
        activeDragStroke = null
    }

    private fun dispatchPath(
        path: Path,
        durationMs: Long,
        onResult: (Boolean) -> Unit
    ) {
        dispatchStroke(
            GestureDescription.StrokeDescription(path, 0, durationMs),
            onResult
        )
    }

    private fun curvedPath(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Path {
        return Path().apply {
            moveTo(startX, startY)
            val dx = endX - startX
            val dy = endY - startY
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                val controlY = (startY + endY) / 2f - kotlin.math.abs(dx) * HORIZONTAL_CURVE_RATIO
                quadTo((startX + endX) / 2f, controlY, endX, endY)
            } else {
                val controlX = (startX + endX) / 2f + kotlin.math.abs(dy) * VERTICAL_CURVE_RATIO
                quadTo(controlX, (startY + endY) / 2f, endX, endY)
            }
        }
    }

    private fun dispatchStroke(
        stroke: GestureDescription.StrokeDescription,
        onResult: (Boolean) -> Unit
    ) {
        dispatchStroke(
            stroke = stroke,
            onCompleted = { onResult(true) },
            onCancelled = { onResult(false) },
        )
    }

    private fun dispatchStroke(
        stroke: GestureDescription.StrokeDescription,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onCancelled()
                }
            },
            null
        )
    }
}
