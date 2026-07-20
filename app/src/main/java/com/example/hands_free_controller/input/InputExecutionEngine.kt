package com.example.hands_free_controller.input

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.hands_free_controller.service.GestureAccessibilityService
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ExecutionCommand
import com.mobileconductor.core.model.ExecutionResult
import com.mobileconductor.core.model.PointerFrame

class InputExecutionEngine(
    private val context: Context
) {
    private var latestPointerFrame: PointerFrame? = null
    private var lastValidPointerFrame: PointerFrame? = null
    private var isDragging: Boolean = false
    private var dragStart: ScreenPoint? = null
    private var dragCurrent: ScreenPoint? = null

    fun updatePointerFrame(frame: PointerFrame) {
        latestPointerFrame = frame

        if (frame.faceDetected) {
            lastValidPointerFrame = frame
        }

        if (isDragging && frame.faceDetected) {
            continueDrag(frame)
        }
    }

    fun execute(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        when (command.commandId) {
            CommandId.TOUCH -> executeTouch(command, onResult)
            CommandId.BACK -> executeBack(command, onResult)
            CommandId.DRAG_START -> executeDragStart(command, onResult)
            CommandId.DRAG_END -> executeDragEnd(command, onResult)
            CommandId.DRAG_CANCEL -> executeDragCancel(command, onResult)
            CommandId.SCROLL_DOWN,
            CommandId.SCROLL_UP,
            CommandId.SCROLL_DOWN_SMALL,
            CommandId.SCROLL_UP_SMALL,
            CommandId.SCROLL_DOWN_LARGE,
            CommandId.SCROLL_UP_LARGE,
            CommandId.NEXT,
            CommandId.PREV -> executeSwipe(command, onResult)

            // 제어 명령(STOP/RESUME/LOCK/UNLOCK)은 OS 이벤트가 아니라 상태 전이 전용이라
            // D가 C로 내려보내지 않는다. 통합 후 CommandId가 17종으로 합쳐지면서
            // when이 이 값들까지 받게 됐는데, 도달하면 D의 배선이 틀린 것이다.
            CommandId.STOP,
            CommandId.RESUME,
            CommandId.LOCK,
            CommandId.UNLOCK -> onResult(
                ExecutionResult(
                    commandId = command.commandId,
                    success = false,
                    x = null,
                    y = null,
                    errorReason = "NOT_AN_OS_EVENT"
                )
            )
        }
    }

    private fun executeBack(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        val service = getService(command, null, onResult) ?: return
        val success = service.back()

        onResult(
            ExecutionResult(
                commandId = command.commandId,
                success = success,
                x = null,
                y = null,
                errorReason = if (success) null else "OS_INJECTION_FAILED"
            )
        )
    }

    private fun executeTouch(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        val pointer = resolvePointer(command, onResult) ?: return
        val service = getService(command, pointer.frame, onResult) ?: return
        val point = toScreenPoint(pointer.frame)

        service.tap(point.x, point.y) { gestureSuccess ->
            onResult(
                command.result(
                    pointer = pointer,
                    gestureSuccess = gestureSuccess
                )
            )
        }
    }

    private fun executeSwipe(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        val pointer = resolvePointer(command, onResult) ?: return
        val service = getService(command, pointer.frame, onResult) ?: return
        val screen = getScreenSize()
        val point = toScreenPoint(pointer.frame, screen)
        val swipe = buildSwipe(command.commandId, point.x, point.y, screen.first, screen.second)

        service.swipe(
            startX = swipe.startX,
            startY = swipe.startY,
            endX = swipe.endX,
            endY = swipe.endY
        ) { gestureSuccess ->
            onResult(
                command.result(
                    pointer = pointer,
                    gestureSuccess = gestureSuccess
                )
            )
        }
    }

    private fun executeDragStart(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        val pointer = resolvePointer(command, onResult) ?: return
        val service = getService(command, pointer.frame, onResult) ?: return
        val start = toScreenPoint(pointer.frame)

        isDragging = true
        dragStart = start
        dragCurrent = start

        service.startDrag(start.x, start.y) { gestureSuccess ->
            onResult(
                command.result(
                    pointer = pointer,
                    gestureSuccess = gestureSuccess
                )
            )
        }
    }

    private fun executeDragEnd(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        if (!isDragging) {
            onResult(command.invalidSequenceResult())
            return
        }

        val pointer = resolvePointer(command, onResult) ?: return
        val service = getService(command, pointer.frame, onResult) ?: return
        val end = toScreenPoint(pointer.frame)
        val start = dragStart

        isDragging = false
        dragCurrent = end

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            service.endDrag(end.x, end.y) { gestureSuccess ->
                clearDrag()
                onResult(command.result(pointer, gestureSuccess))
            }
        } else if (start != null) {
            service.drag(start.x, start.y, end.x, end.y) { gestureSuccess ->
                clearDrag()
                onResult(command.result(pointer, gestureSuccess))
            }
        } else {
            clearDrag()
            onResult(command.invalidSequenceResult())
        }
    }

    private fun executeDragCancel(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        if (!isDragging) {
            onResult(command.invalidSequenceResult())
            return
        }

        GestureAccessibilityService.instance?.cancelDrag()
        clearDrag()

        onResult(
            ExecutionResult(
                commandId = command.commandId,
                success = true,
                x = lastValidPointerFrame?.x,
                y = lastValidPointerFrame?.y,
                errorReason = null
            )
        )
    }

    private fun continueDrag(frame: PointerFrame) {
        val service = GestureAccessibilityService.instance ?: return
        val current = dragCurrent ?: return
        val next = toScreenPoint(frame)

        dragCurrent = next

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            service.continueDrag(
                startX = current.x,
                startY = current.y,
                endX = next.x,
                endY = next.y
            )
        }
    }

    private fun clearDrag() {
        isDragging = false
        dragStart = null
        dragCurrent = null
    }

    private fun resolvePointer(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ): ResolvedPointer? {
        val latest = latestPointerFrame

        if (latest != null && latest.faceDetected) {
            return ResolvedPointer(latest, isStale = false)
        }

        val fallback = lastValidPointerFrame
        if (fallback != null) {
            return ResolvedPointer(fallback, isStale = true)
        }

        onResult(
            ExecutionResult(
                commandId = command.commandId,
                success = false,
                x = null,
                y = null,
                errorReason = "NO_POINTER"
            )
        )
        return null
    }

    private fun getService(
        command: ExecutionCommand,
        pointer: PointerFrame?,
        onResult: (ExecutionResult) -> Unit
    ): GestureAccessibilityService? {
        val service = GestureAccessibilityService.instance
        if (service != null) {
            return service
        }

        onResult(
            ExecutionResult(
                commandId = command.commandId,
                success = false,
                x = pointer?.x,
                y = pointer?.y,
                errorReason = "ACCESSIBILITY_SERVICE_NOT_CONNECTED"
            )
        )
        return null
    }

    private fun toScreenPoint(
        pointer: PointerFrame,
        screen: Pair<Int, Int> = getScreenSize()
    ): ScreenPoint {
        return ScreenPoint(
            x = pointer.x.coerceIn(0f, 1f) * screen.first,
            y = pointer.y.coerceIn(0f, 1f) * screen.second
        )
    }

    private fun buildSwipe(
        commandId: CommandId,
        centerX: Float,
        centerY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): SwipeCoordinates {
        val verticalDistance = screenHeight * scrollRatio(commandId)
        val horizontalDistance = screenWidth * 0.5f

        val (dx, dy) = when (commandId) {
            CommandId.SCROLL_DOWN,
            CommandId.SCROLL_DOWN_SMALL,
            CommandId.SCROLL_DOWN_LARGE -> 0f to -verticalDistance
            CommandId.SCROLL_UP,
            CommandId.SCROLL_UP_SMALL,
            CommandId.SCROLL_UP_LARGE -> 0f to verticalDistance
            CommandId.NEXT -> -horizontalDistance to 0f
            CommandId.PREV -> horizontalDistance to 0f
            else -> 0f to 0f
        }

        val startX = clamp(centerX - dx / 2f, screenWidth)
        val startY = clamp(centerY - dy / 2f, screenHeight)
        val endX = clamp(centerX + dx / 2f, screenWidth)
        val endY = clamp(centerY + dy / 2f, screenHeight)

        return SwipeCoordinates(startX, startY, endX, endY)
    }

    private fun scrollRatio(commandId: CommandId): Float {
        return when (commandId) {
            CommandId.SCROLL_DOWN_SMALL,
            CommandId.SCROLL_UP_SMALL -> 0.2f
            CommandId.SCROLL_DOWN_LARGE,
            CommandId.SCROLL_UP_LARGE -> 0.8f
            else -> 0.5f
        }
    }

    private fun clamp(value: Float, upperBound: Int): Float {
        return value.coerceIn(1f, (upperBound - 1).toFloat())
    }

    private fun getScreenSize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun ExecutionCommand.result(
        pointer: ResolvedPointer,
        gestureSuccess: Boolean
    ): ExecutionResult {
        return ExecutionResult(
            commandId = commandId,
            success = gestureSuccess && !pointer.isStale,
            x = pointer.frame.x,
            y = pointer.frame.y,
            errorReason = when {
                pointer.isStale -> "STALE_POINTER"
                !gestureSuccess -> "OS_INJECTION_FAILED"
                else -> null
            }
        )
    }

    private fun ExecutionCommand.invalidSequenceResult(): ExecutionResult {
        return ExecutionResult(
            commandId = commandId,
            success = false,
            x = lastValidPointerFrame?.x,
            y = lastValidPointerFrame?.y,
            errorReason = "INVALID_SEQUENCE"
        )
    }

    private data class ResolvedPointer(
        val frame: PointerFrame,
        val isStale: Boolean
    )

    private data class ScreenPoint(
        val x: Float,
        val y: Float
    )

    private data class SwipeCoordinates(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )
}
