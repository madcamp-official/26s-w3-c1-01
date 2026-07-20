package com.madcamp.handsfree.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

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
            val collection = firestore.collection("telemetry_events")

            events.forEach { event ->
                batch.set(
                    collection.document(event.eventId),
                    event.toFirestoreMap(),
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
        return mapOf(
            "eventId" to eventId,
            "eventName" to eventName,
            "timestamp" to timestamp,
            "sessionId" to sessionId,
            "deviceModel" to deviceModel,
            "androidVersion" to androidVersion,
            "appVersion" to appVersion,
            "payload" to payload,
            "uploadedAt" to System.currentTimeMillis(),
            "source" to "android",
        )
    }

    private companion object {
        const val TAG = "TelemetryFirebase"
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
