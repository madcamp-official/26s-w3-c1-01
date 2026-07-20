package com.madcamp.handsfree.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UploadResult(
    val success: Boolean,
    val uploadedEventIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

interface TelemetryUploader {
    suspend fun upload(events: List<TelemetryEvent>): UploadResult
}

/** Firebase API가 네트워크/설정 문제로 막혀도 앱 코드가 Firebase에 직접 묶이지 않게 하는 경계. */
class FirebaseTelemetryUploader(
    private val context: Context,
) : TelemetryUploader {
    override suspend fun upload(events: List<TelemetryEvent>): UploadResult = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext UploadResult(success = true)

        runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()
            val rawEvents = firestore.collection("telemetry_events")
            val feedback = firestore.collection("telemetry_feedback")
            val commandStats = firestore.collection("telemetry_command_stats")
            val dailyStats = firestore.collection("telemetry_daily_stats")

            events.forEach { event ->
                batch.set(
                    rawEvents.document(event.eventId),
                    event.toFirestoreMap(),
                    SetOptions.merge(),
                )
                if (event.eventName == TelemetryEventName.USER_FEEDBACK.wireName) {
                    batch.set(
                        feedback.document(event.eventId),
                        event.toFeedbackMap(),
                        SetOptions.merge(),
                    )
                }
                if (event.eventName == TelemetryEventName.COMMAND_EXECUTED.wireName) {
                    val commandId = event.payload["commandId"] ?: "UNKNOWN"
                    batch.set(
                        commandStats.document("${event.localDate()}_$commandId"),
                        event.toCommandStatsUpdate(commandId),
                        SetOptions.merge(),
                    )
                }
                batch.set(
                    dailyStats.document(event.localDate()),
                    event.toDailyStatsUpdate(),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }.fold(
            onSuccess = {
                Log.i(TAG, "Uploaded ${events.size} telemetry events to Firestore")
                UploadResult(
                    success = true,
                    uploadedEventIds = events.map { it.eventId }.toSet(),
                )
            },
            onFailure = { error ->
                Log.w(TAG, "Firebase upload failed; keeping ${events.size} telemetry events in local queue", error)
                UploadResult(
                    success = false,
                    errorMessage = error.message ?: error::class.java.simpleName,
                )
            },
        )
    }

    private fun TelemetryEvent.toFirestoreMap(): Map<String, Any> {
        return mutableMapOf<String, Any>(
            "eventId" to eventId,
            "eventName" to eventName,
            "category" to category(),
            "timestamp" to timestamp,
            "eventDate" to localDate(),
            "sessionId" to sessionId,
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion,
            "appVersion" to appVersion,
            "payload" to payload,
            "uploadedAt" to System.currentTimeMillis(),
            "source" to "android",
        ).apply {
            payload["commandId"]?.let { put("commandId", it) }
            payload["success"]?.toBooleanStrictOrNull()?.let { put("success", it) }
            payload["errorReason"]?.let { put("errorReason", it) }
            payload["type"]?.let { put("errorType", it) }
        }
    }

    private fun TelemetryEvent.toFeedbackMap(): Map<String, Any> {
        return mapOf(
            "eventId" to eventId,
            "eventDate" to localDate(),
            "timestamp" to timestamp,
            "uploadedAt" to System.currentTimeMillis(),
            "sessionId" to sessionId,
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion,
            "appVersion" to appVersion,
            "message" to (payload["message"] ?: ""),
            "failedCommandSituation" to (payload["failedCommandSituation"] ?: ""),
        )
    }

    private fun TelemetryEvent.toCommandStatsUpdate(commandId: String): Map<String, Any> {
        val success = payload["success"]?.toBooleanStrictOrNull() == true
        val errorReason = payload["errorReason"].orEmpty()
        return mutableMapOf<String, Any>(
            "date" to localDate(),
            "commandId" to commandId,
            "totalCount" to FieldValue.increment(1),
            "successCount" to FieldValue.increment(if (success) 1 else 0),
            "failureCount" to FieldValue.increment(if (success) 0 else 1),
            "lastUpdatedAt" to System.currentTimeMillis(),
        ).apply {
            if (!success && errorReason.isNotBlank()) {
                put("failureReasons.$errorReason", FieldValue.increment(1))
            }
            if (commandId == "DRAG_START" && success) {
                put("dragStartSuccessCount", FieldValue.increment(1))
            }
            if (commandId == "DRAG_END" && success) {
                put("dragEndSuccessCount", FieldValue.increment(1))
            }
        }
    }

    private fun TelemetryEvent.toDailyStatsUpdate(): Map<String, Any> {
        val update = mutableMapOf<String, Any>(
            "date" to localDate(),
            "lastUpdatedAt" to System.currentTimeMillis(),
            "totalEventCount" to FieldValue.increment(1),
        )

        when (eventName) {
            TelemetryEventName.APP_OPENED.wireName ->
                update["appOpenedCount"] = FieldValue.increment(1)
            TelemetryEventName.CALIBRATION_STARTED.wireName ->
                update["calibrationStartedCount"] = FieldValue.increment(1)
            TelemetryEventName.CALIBRATION_COMPLETED.wireName -> {
                update["calibrationCompletedCount"] = FieldValue.increment(1)
                payload["durationMs"]?.toLongOrNull()?.let {
                    update["calibrationDurationMsSum"] = FieldValue.increment(it)
                    update["calibrationDurationSamples"] = FieldValue.increment(1)
                }
            }
            TelemetryEventName.CALIBRATION_FAILED.wireName ->
                update["calibrationFailedCount"] = FieldValue.increment(1)
            TelemetryEventName.COMMAND_EXECUTED.wireName -> {
                val success = payload["success"]?.toBooleanStrictOrNull() == true
                update["commandExecutedCount"] = FieldValue.increment(1)
                update["commandSuccessCount"] = FieldValue.increment(if (success) 1 else 0)
                update["commandFailureCount"] = FieldValue.increment(if (success) 0 else 1)
                payload["commandId"]?.let {
                    update["commands.$it"] = FieldValue.increment(1)
                }
                payload["errorReason"]?.takeIf { it.isNotBlank() }?.let {
                    update["failureReasons.$it"] = FieldValue.increment(1)
                }
                payload["voiceToExecutionMs"]?.toLongOrNull()?.let {
                    update["voiceToExecutionMsSum"] = FieldValue.increment(it)
                    update["voiceToExecutionSamples"] = FieldValue.increment(1)
                }
            }
            TelemetryEventName.PERFORMANCE_SUMMARY.wireName -> {
                update["performanceSummaryCount"] = FieldValue.increment(1)
                payload["avgPointerFps"]?.toDoubleOrNull()?.let {
                    update["avgPointerFpsSum"] = FieldValue.increment(it)
                    update["avgPointerFpsSamples"] = FieldValue.increment(1)
                }
                payload["faceLostCount"]?.toLongOrNull()?.let {
                    update["faceLostCount"] = FieldValue.increment(it)
                }
                payload["voiceFailureRate"]?.toDoubleOrNull()?.let {
                    update["voiceFailureRateSum"] = FieldValue.increment(it)
                    update["voiceFailureRateSamples"] = FieldValue.increment(1)
                }
            }
            TelemetryEventName.APP_ERROR.wireName -> {
                update["appErrorCount"] = FieldValue.increment(1)
                payload["type"]?.let { update["errorTypes.$it"] = FieldValue.increment(1) }
                payload["reason"]?.let { update["errorReasons.$it"] = FieldValue.increment(1) }
            }
            TelemetryEventName.USER_FEEDBACK.wireName ->
                update["feedbackCount"] = FieldValue.increment(1)
        }

        return update
    }

    private fun TelemetryEvent.category(): String = when (eventName) {
        TelemetryEventName.APP_OPENED.wireName,
        TelemetryEventName.CALIBRATION_STARTED.wireName,
        TelemetryEventName.CALIBRATION_COMPLETED.wireName,
        TelemetryEventName.CALIBRATION_FAILED.wireName -> "usage"
        TelemetryEventName.COMMAND_EXECUTED.wireName -> "command"
        TelemetryEventName.PERFORMANCE_SUMMARY.wireName -> "performance"
        TelemetryEventName.APP_ERROR.wireName -> "error"
        TelemetryEventName.USER_FEEDBACK.wireName -> "feedback"
        else -> "unknown"
    }

    private fun TelemetryEvent.localDate(): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DATE_FORMATTER)
    }

    private companion object {
        const val TAG = "TelemetryFirebase"
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

/**
 * 로컬 검증용 업로더. 서버 대신 Logcat에 JSON batch를 출력하고 성공 처리한다.
 * Firebase가 막혔을 때 큐/Worker 동작만 검증하는 데 쓴다.
 */
class LogcatTelemetryUploader : TelemetryUploader {
    override suspend fun upload(events: List<TelemetryEvent>): UploadResult = withContext(Dispatchers.IO) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        Log.i(TAG, "Telemetry upload batch: $array")
        UploadResult(
            success = true,
            uploadedEventIds = events.map { it.eventId }.toSet(),
        )
    }

    private companion object {
        const val TAG = "TelemetryLogcat"
    }
}
