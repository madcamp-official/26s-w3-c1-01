package com.example.hands_free_controller.input

enum class CommandId {
    TOUCH,
    BACK,
    DRAG_START,
    DRAG_END,
    DRAG_CANCEL,
    SCROLL_DOWN,
    SCROLL_UP,
    SCROLL_DOWN_SMALL,
    SCROLL_UP_SMALL,
    SCROLL_DOWN_LARGE,
    SCROLL_UP_LARGE,
    NEXT,
    PREV
}

data class ExecutionCommand(
    val commandId: CommandId,
    val timestamp: Long = System.currentTimeMillis()
)
