package com.madcamp.handsfree.telemetry

import android.content.Context
import org.json.JSONArray

class LocalTelemetryQueue(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(event: TelemetryEvent) {
        val events = readAll().toMutableList()
        events += event
        writeAll(events.takeLast(MAX_EVENTS))
    }

    @Synchronized
    fun peek(limit: Int = DEFAULT_UPLOAD_LIMIT): List<TelemetryEvent> {
        return readAll().take(limit)
    }

    @Synchronized
    fun removeUploaded(eventIds: Set<String>) {
        if (eventIds.isEmpty()) return
        writeAll(readAll().filterNot { it.eventId in eventIds })
    }

    @Synchronized
    fun count(): Int = readAll().size

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_EVENTS).apply()
    }

    private fun readAll(): List<TelemetryEvent> {
        val raw = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                runCatching { TelemetryEvent.fromJson(item) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun writeAll(events: List<TelemetryEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    companion object {
        const val DEFAULT_UPLOAD_LIMIT = 200
        private const val MAX_EVENTS = 2_000
        private const val PREFS = "telemetry_queue"
        private const val KEY_EVENTS = "events"
    }
}
