package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import com.madcamp.handsfree.tracking.PointerTracker
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.orchestrator.port.CalibrationConsumer

/**
 * D → A/HAND 경계 구현. 완성된 프로파일을 **현재 활성** 트래커에 주입하고 그 모드의 저장소에 남긴다.
 *
 * Phase 3에서 트래커가 런타임에 바뀌므로 특정 트래커를 고정 참조하지 않고 [activeTracker]
 * 프로바이더로 매번 현재 것을 가져온다. 저장도 [currentMode]로 모드별 저장소에 나눈다
 * (FACE 각도 범위 / HAND 손 도달범위는 의미가 달라 섞으면 안 된다).
 *
 * **A는 `referencePoints`(9점)를 보간에 쓰지 않는다.** faceRange* min/max 선형
 * 정규화만 한다(OPEN_ISSUES #6). D가 9점 보간을 A쪽 책임으로 기대했다면 여기가
 * 어긋나는 지점이다 — 정확도 이슈가 나오면 이 주석을 먼저 볼 것.
 * (HAND 모드에서는 faceRange*가 손 도달범위 x/y를 담는다 — MOTION_CAPTURE_PLAN 축 1.)
 */
class TrackerCalibrationConsumer(
    private val context: Context,
    private val activeTracker: () -> PointerTracker?,
    private val currentMode: () -> InputMode,
) : CalibrationConsumer {

    override fun onProfileReady(profile: CalibrationProfile) {
        val mode = currentMode()
        Log.i(
            TAG,
            "mode=$mode profile=${profile.profileId} " +
                "yaw=[${profile.faceRangeYawMin}, ${profile.faceRangeYawMax}] " +
                "pitch=[${profile.faceRangePitchMin}, ${profile.faceRangePitchMax}] " +
                "sensitivity=${profile.sensitivityLevel} smoothing=${profile.smoothingLevel}",
        )
        activeTracker()?.updateProfile(profile)
        // 저장이 없으면 앱을 껐다 켤 때마다 22초짜리 보정을 다시 해야 한다
        CalibrationStore.save(context, mode, profile)
    }

    private companion object {
        const val TAG = "HF-Calib"
    }
}
