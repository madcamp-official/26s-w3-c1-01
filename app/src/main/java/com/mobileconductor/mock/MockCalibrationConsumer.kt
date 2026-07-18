package com.mobileconductor.mock

import android.util.Log
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.orchestrator.port.CalibrationConsumer

/**
 * A 대체 Mock (명세 forD 7절). 완성된 [CalibrationProfile]을 보관/로깅만 한다.
 *
 * 실제 A는 이 프로파일을 로컬 저장하고 포인터 매핑에 사용한다.
 */
class MockCalibrationConsumer : CalibrationConsumer {

    /** 마지막으로 수신한 프로파일(디버그/확인용). */
    @Volatile
    var lastProfile: CalibrationProfile? = null
        private set

    override fun onProfileReady(profile: CalibrationProfile) {
        lastProfile = profile
        Log.d("MockCalibration", "profile ready: $profile")
    }
}
