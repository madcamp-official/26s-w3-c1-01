package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import com.madcamp.handsfree.tracking.FaceTracker
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.orchestrator.port.CalibrationConsumer

/**
 * D → A 경계 구현. 완성된 프로파일을 [FaceTracker]에 주입하고 로컬에 저장한다.
 *
 * 재보정 시에도 같은 경로로 들어오고, A는 다음 프레임부터 즉시 새 범위로 매핑한다.
 *
 * **A는 `referencePoints`(9점)를 보간에 쓰지 않는다.** faceRange* min/max 선형
 * 정규화만 한다(OPEN_ISSUES #6). D가 9점 보간을 A쪽 책임으로 기대했다면 여기가
 * 어긋나는 지점이다 — 정확도 이슈가 나오면 이 주석을 먼저 볼 것.
 */
class TrackerCalibrationConsumer(
    private val context: Context,
    private val tracker: FaceTracker,
) : CalibrationConsumer {

    override fun onProfileReady(profile: CalibrationProfile) {
        Log.i(
            TAG,
            "profile=${profile.profileId} " +
                "yaw=[${profile.faceRangeYawMin}, ${profile.faceRangeYawMax}] " +
                "pitch=[${profile.faceRangePitchMin}, ${profile.faceRangePitchMax}] " +
                "sensitivity=${profile.sensitivityLevel} smoothing=${profile.smoothingLevel}",
        )
        tracker.updateProfile(profile)
        // 저장이 없으면 앱을 껐다 켤 때마다 22초짜리 보정을 다시 해야 한다
        CalibrationStore.save(context, profile)
    }

    private companion object {
        const val TAG = "HF-Calib"
    }
}
