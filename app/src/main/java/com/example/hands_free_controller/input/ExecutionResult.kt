package com.example.hands_free_controller.input

data class ExecutionResult(
    val commandId: CommandId,
    val success: Boolean,
    val executedAt: PointerFrame?,
    val timestamp: Long = System.currentTimeMillis(),
    val errorReason: String? = null
)
