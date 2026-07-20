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

    /**
     * 설치 단위로 고정되는 식별자. 앱을 지우고 다시 깔면 새로 생긴다.
     *
     * **원래 이게 `sessionId`라는 이름으로 있었다.** 이름과 실제가 달랐던 것이지
     * 존재 자체가 문제는 아니다 — 옵션 2가 요구하는 "공개 경로로 유입된 실제 사용자"를
     * 세려면 설치를 구분할 방법이 반드시 필요하고, `telemetry_users`의
     * `totalUserCount`가 이 값에 걸려 있다.
     *
     * 다만 이건 익명이 아니라 가명(pseudonymous)이다. 기기 모델·Android 버전과 묶이면
     * 한 사람의 전 사용 이력이 하나로 연결된다. **그래서 화면 고지 문구를 "익명"에서
     * 정확한 표현으로 바꿨다.** 지표를 포기하는 대신 사실대로 말하는 쪽을 택한 것이다.
     */
    val installId: String
        get() {
            val existing = prefs.getString(KEY_INSTALL_ID, null)
            if (existing != null) return existing

            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, created).apply()
            return created
        }

    /**
     * 앱 실행(프로세스) 단위 식별자. **저장하지 않는다.**
     *
     * 한 번의 사용 흐름(실행 → 보정 → 명령들)을 묶어 보는 용도다. 설치 식별자와
     * 분리해 둬야 "이 실행에서 명령이 몇 번 실패했나" 같은 걸 볼 때
     * 사용자 전체 이력을 끌어오지 않아도 된다.
     */
    val sessionId: String get() = processSessionId

    companion object {
        private const val PREFS = "telemetry_settings"
        private const val KEY_DIAGNOSTICS_ENABLED = "diagnostics_enabled"
        // 옛 이름 그대로 둔다. 키를 바꾸면 기존 설치가 전부 신규 사용자로 잡혀서
        // totalUserCount가 한 번 부풀어 오른다
        private const val KEY_INSTALL_ID = "session_id"

        private val processSessionId: String = UUID.randomUUID().toString()
    }
}
