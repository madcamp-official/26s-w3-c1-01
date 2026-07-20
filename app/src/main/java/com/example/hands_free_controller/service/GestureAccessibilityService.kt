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
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        dispatchPath(path, durationMs, onResult)
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

    private fun dispatchStroke(
        stroke: GestureDescription.StrokeDescription,
        onResult: (Boolean) -> Unit
    ) {
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onResult(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onResult(false)
                }
            },
            null
        )
    }
}
