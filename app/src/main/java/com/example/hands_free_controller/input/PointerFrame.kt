package com.example.hands_free_controller.input

data class PointerFrame(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val faceDetected: Boolean,
    val confidence: Float
)
