package com.madcamp.handsfree.telemetry

import android.content.Context
import java.util.UUID

class TelemetrySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var diagnosticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTICS_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DIAGNOSTICS_ENABLED, value).apply()
        }

    val sessionId: String
        get() {
            val existing = prefs.getString(KEY_SESSION_ID, null)
            if (existing != null) return existing

            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_SESSION_ID, created).apply()
            return created
        }

    companion object {
        private const val PREFS = "telemetry_settings"
        private const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
        private const val KEY_SESSION_ID = "session_id"
    }
}
