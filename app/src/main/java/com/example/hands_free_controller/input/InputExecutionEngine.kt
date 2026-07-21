package com.example.hands_free_controller.input

import android.content.Context
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

    fun updatePointerFrame(frame: PointerFrame) {
        latestPointerFrame = frame

        if (frame.faceDetected) {
            lastValidPointerFrame = frame
        }
    }

    fun execute(
        command: ExecutionCommand,
        onResult: (ExecutionResult) -> Unit
    ) {
        when (command.commandId) {
            CommandId.TOUCH -> executeTouch(command, onResult)
            CommandId.BACK -> executeBack(command, onResult)
            CommandId.SCROLL_DOWN,
            CommandId.SCROLL_UP,
            CommandId.NEXT,
            CommandId.PREV -> executeSwipe(command, onResult)

            // 제어 명령(LOCK/UNLOCK)은 OS 이벤트가 아니라 상태 전이 전용이라 D가 C로 내려보내지 않는다.
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

        service.flingSwipe(
            startX = swipe.startX,
            startY = swipe.startY,
            endX = swipe.endX,
            endY = swipe.endY,
            warmupDurationMs = warmupDurationFor(command.commandId),
            flingDurationMs = flingDurationFor(command.commandId),
        ) { gestureSuccess ->
            onResult(
                command.result(
                    pointer = pointer,
                    gestureSuccess = gestureSuccess
                )
            )
        }
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
        when (commandId) {
            CommandId.SCROLL_DOWN -> {
                val x = centerX.coerceIn(screenWidth * 0.25f, screenWidth * 0.75f)
                return SwipeCoordinates(
                    startX = x,
                    startY = screenHeight * VERTICAL_SCROLL_START_RATIO,
                    endX = x,
                    endY = screenHeight * VERTICAL_SCROLL_END_RATIO,
                )
            }
            CommandId.SCROLL_UP -> {
                val x = centerX.coerceIn(screenWidth * 0.25f, screenWidth * 0.75f)
                return SwipeCoordinates(
                    startX = x,
                    startY = screenHeight * VERTICAL_SCROLL_END_RATIO,
                    endX = x,
                    endY = screenHeight * VERTICAL_SCROLL_START_RATIO,
                )
            }
            CommandId.NEXT -> {
                val y = screenHeight * HORIZONTAL_PAGE_SWIPE_Y_RATIO
                return SwipeCoordinates(
                    startX = screenWidth * HORIZONTAL_PAGE_START_RATIO,
                    startY = y,
                    endX = screenWidth * HORIZONTAL_PAGE_END_RATIO,
                    endY = y,
                )
            }
            CommandId.PREV -> {
                val y = screenHeight * HORIZONTAL_PAGE_SWIPE_Y_RATIO
                return SwipeCoordinates(
                    startX = screenWidth * HORIZONTAL_PAGE_END_RATIO,
                    startY = y,
                    endX = screenWidth * HORIZONTAL_PAGE_START_RATIO,
                    endY = y,
                )
            }
            else -> Unit
        }

        return SwipeCoordinates(centerX, centerY, centerX, centerY)
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

    private fun warmupDurationFor(commandId: CommandId): Long = when (commandId) {
        CommandId.NEXT, CommandId.PREV -> PAGE_SWIPE_WARMUP_DURATION_MS
        else -> SCROLL_SWIPE_WARMUP_DURATION_MS
    }

    private fun flingDurationFor(commandId: CommandId): Long = when (commandId) {
        CommandId.NEXT, CommandId.PREV -> PAGE_SWIPE_FLING_DURATION_MS
        else -> SCROLL_SWIPE_FLING_DURATION_MS
    }

    /**
     * 스크롤 한 번에 화면의 몇 배를 움직일지.
     *
     * 실기기에서 기본값(0.5)으로는 "화면이 안 넘어간다"는 평가가 나왔다. 거리도
     * 문제였지만 **속도가 더 컸다** — 스와이프 시간을 줄여 플링으로 인식되게 한 것과
     * 함께 봐야 한다([GestureAccessibilityService]의 duration).
     */
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
        const val SCROLL_SWIPE_DURATION_MS = 260L
        const val SCROLL_SWIPE_WARMUP_DURATION_MS = 205L
        const val SCROLL_SWIPE_FLING_DURATION_MS = 55L

        /**
         * 페이지 넘김(좌우) 스와이프 시간. **스크롤보다 길어야 한다.**
         *
         * 런처는 속도가 아니라 "손가락을 따라 페이지를 끌다가 절반을 넘겼는가"로
         * 판정하므로 중간 이동 이벤트가 촘촘해야 한다. 160ms로는 샘플이 부족해
         * 아이콘에 눌림 모션만 나타나고 페이지가 넘어가지 않았다.
         */
        const val PAGE_SWIPE_DURATION_MS = 280L
        const val PAGE_SWIPE_WARMUP_DURATION_MS = 225L
        const val PAGE_SWIPE_FLING_DURATION_MS = 55L

        /**
         * 좌우 스와이프(NEXT/PREV) 거리. 화면 폭 대비 비율.
         *
         * 페이지 넘김은 보통 "화면 폭의 절반 이상을 넘겼는가" 또는 "속도가 충분한가"로
         * 판정한다. 0.5는 경계선이라 넘어갈 때와 아닐 때가 갈렸다.
         */
        const val VERTICAL_SCROLL_START_RATIO = 0.85f
        const val VERTICAL_SCROLL_END_RATIO = 0.20f
        const val HORIZONTAL_PAGE_START_RATIO = 0.85f
        const val HORIZONTAL_PAGE_END_RATIO = 0.15f
        const val HORIZONTAL_PAGE_SWIPE_Y_RATIO = 0.72f

    }
}
