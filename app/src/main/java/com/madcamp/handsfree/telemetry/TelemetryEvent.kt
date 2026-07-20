package com.madcamp.handsfree.telemetry

import android.os.Build
import org.json.JSONObject
import java.util.UUID

enum class TelemetryEventName(val wireName: String) {
    APP_OPENED("app_opened"),
    CALIBRATION_STARTED("calibration_started"),
    CALIBRATION_COMPLETED("calibration_completed"),
    CALIBRATION_FAILED("calibration_failed"),
    COMMAND_EXECUTED("command_executed"),
    PERFORMANCE_SUMMARY("performance_summary"),
    APP_ERROR("app_error"),
    USER_FEEDBACK("user_feedback"),
}

data class TelemetryEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val deviceModel: String = Build.MODEL ?: "unknown",
    val androidVersion: String = Build.VERSION.RELEASE ?: "unknown",
    val appVersion: String,
    val payload: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject {
        val payloadJson = JSONObject()
        payload.forEach { (key, value) -> payloadJson.put(key, value) }

        return JSONObject()
            .put("eventId", eventId)
            .put("eventName", eventName)
            .put("timestamp", timestamp)
            .put("sessionId", sessionId)
            .put("deviceModel", deviceModel)
            .put("androidVersion", androidVersion)
            .put("appVersion", appVersion)
            .put("payload", payloadJson)
    }

    companion object {
        fun fromJson(json: JSONObject): TelemetryEvent {
            val payloadJson = json.optJSONObject("payload") ?: JSONObject()
            val payload = buildMap {
                payloadJson.keys().forEach { key ->
                    put(key, payloadJson.optString(key))
                }
            }

            return TelemetryEvent(
                eventId = json.getString("eventId"),
                eventName = json.getString("eventName"),
                timestamp = json.getLong("timestamp"),
                sessionId = json.getString("sessionId"),
                deviceModel = json.getString("deviceModel"),
                androidVersion = json.getString("androidVersion"),
                appVersion = json.getString("appVersion"),
                payload = payload,
            )
        }
    }
}
