package com.madcamp.handsfree.telemetry

import android.content.Context
import android.content.pm.PackageManager
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ExecutionResult

interface TelemetryLogger {
    fun logAppOpened()
    fun logCalibrationStarted()
    fun logCalibrationCompleted(durationMs: Long)
    fun logCalibrationFailed(reason: String, retryCount: Int? = null)
    fun logCommandExecuted(result: ExecutionResult, voiceToExecutionMs: Long? = null)
    fun logPerformanceSummary(
        avgPointerFps: Float? = null,
        voiceToExecutionAvgMs: Long? = null,
        faceLostCount: Int? = null,
        voiceFailureRate: Float? = null,
    )
    fun logAppError(type: String, message: String? = null)
    fun logVoiceRecognitionFailed(reason: String)
    fun logUserFeedback(message: String, failedCommandSituation: String?)
}

class DefaultTelemetryLogger(
    context: Context,
    private val settings: TelemetrySettings = TelemetrySettings(context),
    private val queue: LocalTelemetryQueue = LocalTelemetryQueue(context),
) : TelemetryLogger {
    private val appContext = context.applicationContext
    private val appVersion = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.versionName ?: "unknown"
    }.getOrElse { "unknown" }

    override fun logAppOpened() = enqueue(TelemetryEventName.APP_OPENED)

    override fun logCalibrationStarted() = enqueue(TelemetryEventName.CALIBRATION_STARTED)

    override fun logCalibrationCompleted(durationMs: Long) = enqueue(
        TelemetryEventName.CALIBRATION_COMPLETED,
        "durationMs" to durationMs.toString(),
    )

    override fun logCalibrationFailed(reason: String, retryCount: Int?) = enqueue(
        TelemetryEventName.CALIBRATION_FAILED,
        "reason" to reason,
        "retryCount" to (retryCount?.toString() ?: ""),
    )

    override fun logCommandExecuted(result: ExecutionResult, voiceToExecutionMs: Long?) = enqueue(
        TelemetryEventName.COMMAND_EXECUTED,
        "commandId" to result.commandId.name,
        "success" to result.success.toString(),
        "errorReason" to (result.errorReason ?: ""),
        "x" to (result.x?.toString() ?: ""),
        "y" to (result.y?.toString() ?: ""),
        "voiceToExecutionMs" to (voiceToExecutionMs?.toString() ?: ""),
    )

    override fun logPerformanceSummary(
        avgPointerFps: Float?,
        voiceToExecutionAvgMs: Long?,
        faceLostCount: Int?,
        voiceFailureRate: Float?,
    ) = enqueue(
        TelemetryEventName.PERFORMANCE_SUMMARY,
        "avgPointerFps" to (avgPointerFps?.toString() ?: ""),
        "voiceToExecutionAvgMs" to (voiceToExecutionAvgMs?.toString() ?: ""),
        "faceLostCount" to (faceLostCount?.toString() ?: ""),
        "voiceFailureRate" to (voiceFailureRate?.toString() ?: ""),
    )

    override fun logAppError(type: String, message: String?) = enqueue(
        TelemetryEventName.APP_ERROR,
        "type" to type,
        "message" to (message ?: ""),
    )

    override fun logVoiceRecognitionFailed(reason: String) = enqueue(
        TelemetryEventName.APP_ERROR,
        "type" to "VOICE_RECOGNITION_FAILED",
        "reason" to reason,
    )

    override fun logUserFeedback(message: String, failedCommandSituation: String?) = enqueue(
        TelemetryEventName.USER_FEEDBACK,
        "message" to message.take(MAX_FEEDBACK_LENGTH),
        "failedCommandSituation" to (failedCommandSituation?.take(MAX_FEEDBACK_LENGTH) ?: ""),
    )

    private fun enqueue(name: TelemetryEventName, vararg payload: Pair<String, String>) {
        if (!settings.diagnosticsEnabled) return
        queue.enqueue(
            TelemetryEvent(
                eventName = name.wireName,
                sessionId = settings.sessionId,
                appVersion = appVersion,
                payload = payload.toMap().filterValues { it.isNotBlank() },
            )
        )
    }

    private companion object {
        const val MAX_FEEDBACK_LENGTH = 1_000
    }
}

object Telemetry {
    @Volatile
    private var logger: TelemetryLogger? = null

    fun logger(context: Context): TelemetryLogger {
        return logger ?: synchronized(this) {
            logger ?: DefaultTelemetryLogger(context.applicationContext).also { logger = it }
        }
    }

    fun resetForTests() {
        logger = null
    }
}
