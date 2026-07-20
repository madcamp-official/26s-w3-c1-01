package com.example.hands_free_controller.input

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
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
        val duration = swipeDurationFor(command.commandId)

        // 스와이프가 "성공"으로 찍히는데도 화면이 안 움직이는 상황이 반복돼서,
        // 실제로 어떤 좌표가 주입되는지 남긴다. 화면 크기·포인터 좌표를 손으로
        // 계산해 추론하다 두 번 틀렸다 — 값을 더 바꾸기 전에 실측이 먼저다.
        Log.i(
            TAG,
            "%s 화면=%dx%d 포인터=(%.0f, %.0f) 스와이프=(%.0f, %.0f)->(%.0f, %.0f) %dms".format(
                command.commandId, screen.first, screen.second,
                point.x, point.y,
                swipe.startX, swipe.startY, swipe.endX, swipe.endY, duration,
            ),
        )

        service.swipe(
            startX = swipe.startX,
            startY = swipe.startY,
            endX = swipe.endX,
            endY = swipe.endY,
            durationMs = duration,
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
        val horizontalDistance = screenWidth * HORIZONTAL_SWIPE_RATIO

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

        val (startX, endX) = fitSegment(centerX, dx, screenWidth)
        val (startY, endY) = fitSegment(centerY, dy, screenHeight)

        return SwipeCoordinates(startX, startY, endX, endY)
    }

    /**
     * 포인터를 중심으로 [delta]만큼의 구간을 만들되, 화면 밖으로 나가면 **잘라내지 않고
     * 통째로 밀어 넣는다.**
     *
     * 예전에는 양 끝을 각각 화면 안으로 clamp했는데, 그러면 포인터가 화면 가장자리에
     * 있을 때 스와이프 길이가 조용히 짧아진다. 같은 명령인데 포인터 위치에 따라
     * 넘어가기도 하고 안 넘어가기도 해서 원인을 짐작하기 어렵다.
     *
     * 가장자리에 여백을 두는 이유는 화면 맨 끝에서 시작하는 제스처가 시스템의
     * 뒤로가기/홈 제스처로 가로채이기 때문이다.
     */
    private fun fitSegment(center: Float, delta: Float, bound: Int): Pair<Float, Float> {
        val margin = bound * EDGE_MARGIN_RATIO
        val min = margin
        val max = bound - margin
        // 화면보다 긴 스와이프는 만들 수 없다
        val d = delta.coerceIn(-(max - min), max - min)

        val start = center - d / 2f
        val end = center + d / 2f
        val shift = when {
            minOf(start, end) < min -> min - minOf(start, end)
            maxOf(start, end) > max -> max - maxOf(start, end)
            else -> 0f
        }
        return (start + shift) to (end + shift)
    }

    /**
     * 스크롤과 페이지 넘김은 OS가 판정하는 방식이 달라 스와이프 시간도 달라야 한다.
     *
     * - **스크롤(위/아래)**: 손을 뗀 순간의 *속도*로 관성 스크롤이 걸린다. 짧을수록 좋다.
     * - **페이지 넘김(좌우)**: 런처가 손가락을 *따라가며* 페이지를 끌다가 절반을 넘겼는지
     *   보고 확정한다. 중간 이동 이벤트가 충분해야 하므로 **길어야 한다.**
     *
     * 실기기에서 좌우에 스크롤과 같은 160ms를 썼더니, 아이콘에 눌림 모션만 보이고
     * 페이지가 넘어가지 않았다 — 런처가 "누르고 뗐다"로 본 것이다.
     */
    private fun swipeDurationFor(commandId: CommandId): Long = when (commandId) {
        CommandId.NEXT, CommandId.PREV -> PAGE_SWIPE_DURATION_MS
        else -> SCROLL_SWIPE_DURATION_MS
    }

    /**
     * 스크롤 한 번에 화면의 몇 배를 움직일지.
     *
     * 실기기에서 기본값(0.5)으로는 "화면이 안 넘어간다"는 평가가 나왔다. 거리도
     * 문제였지만 **속도가 더 컸다** — 스와이프 시간을 줄여 플링으로 인식되게 한 것과
     * 함께 봐야 한다([GestureAccessibilityService]의 duration).
     */
    private fun scrollRatio(commandId: CommandId): Float {
        return when (commandId) {
            CommandId.SCROLL_DOWN_SMALL,
            CommandId.SCROLL_UP_SMALL -> 0.35f
            CommandId.SCROLL_DOWN_LARGE,
            CommandId.SCROLL_UP_LARGE -> 0.9f
            else -> 0.65f
        }
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

    private companion object {
        const val TAG = "HF-Input"

        /**
         * 스크롤(위/아래) 스와이프 시간.
         *
         * 안드로이드는 손을 뗀 순간의 **속도**로 관성 스크롤을 건다. 320ms로 천천히
         * 끌면 화면이 조금 움직였다가 임계값을 못 넘고 제자리로 돌아온다.
         * 너무 줄이면(50ms 미만) 이벤트가 몇 개 안 생겨 속도 계산이 불안정해진다.
         */
        const val SCROLL_SWIPE_DURATION_MS = 160L

        /**
         * 페이지 넘김(좌우) 스와이프 시간. **스크롤보다 길어야 한다.**
         *
         * 런처는 속도가 아니라 "손가락을 따라 페이지를 끌다가 절반을 넘겼는가"로
         * 판정하므로 중간 이동 이벤트가 촘촘해야 한다. 160ms로는 샘플이 부족해
         * 아이콘에 눌림 모션만 나타나고 페이지가 넘어가지 않았다.
         */
        const val PAGE_SWIPE_DURATION_MS = 400L

        /**
         * 좌우 스와이프(NEXT/PREV) 거리. 화면 폭 대비 비율.
         *
         * 페이지 넘김은 보통 "화면 폭의 절반 이상을 넘겼는가" 또는 "속도가 충분한가"로
         * 판정한다. 0.5는 경계선이라 넘어갈 때와 아닐 때가 갈렸다.
         */
        const val HORIZONTAL_SWIPE_RATIO = 0.75f

        /**
         * 화면 가장자리에서 띄울 여백 비율.
         *
         * 화면 맨 끝에서 시작하는 제스처는 시스템의 뒤로가기/홈 제스처로 가로채여
         * 앱에 전달되지 않는다.
         */
        const val EDGE_MARGIN_RATIO = 0.05f
    }
}
