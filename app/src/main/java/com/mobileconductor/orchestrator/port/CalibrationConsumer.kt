package com.mobileconductor.orchestrator.port

import com.mobileconductor.core.model.CalibrationProfile

/**
 * D → A 경계. 완성된 [CalibrationProfile]을 A에게 전달한다.
 *
 * 재보정 시에는 완료 시점에 원자적으로 새 프로파일로 교체한다(명세 forD 3절).
 * 실제 구현은 A의 프로파일 저장소, 또는 Mock으로 교체된다.
 */
interface CalibrationConsumer {
    fun onProfileReady(profile: CalibrationProfile)
}
