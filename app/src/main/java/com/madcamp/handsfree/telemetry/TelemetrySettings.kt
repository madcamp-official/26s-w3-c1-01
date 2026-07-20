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

    init {
        // 예전 버전이 SharedPreferences에 영구 저장하던 식별자를 지운다.
        // 업데이트한 기존 사용자에게 옛날 ID가 남아 있으면 이 변경이 의미가 없다.
        if (prefs.contains(KEY_LEGACY_SESSION_ID)) {
            prefs.edit().remove(KEY_LEGACY_SESSION_ID).apply()
        }
    }

    /**
     * 프로세스가 살아 있는 동안만 유지되는 식별자. **저장하지 않는다.**
     *
     * 원래는 SharedPreferences에 한 번 만들고 앱을 지울 때까지 그대로 썼는데,
     * 그건 세션 ID가 아니라 영구 설치 식별자다. 기기 모델·Android 버전과 묶이면
     * 한 사람의 전 사용 이력이 하나로 연결돼서, 앱이 화면에서 약속한
     * "**익명** 진단 데이터"가 성립하지 않는다.
     *
     * 앱 실행 단위로 끊으면 이름 그대로의 의미가 되고, 한 번의 사용 흐름
     * (실행 → 보정 → 명령들)을 묶어 보는 분석 목적에도 이쪽이 오히려 맞다.
     */
    val sessionId: String get() = processSessionId

    companion object {
        private const val PREFS = "telemetry_settings"
        private const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
        private const val KEY_LEGACY_SESSION_ID = "session_id"

        private val processSessionId: String = UUID.randomUUID().toString()
    }
}
