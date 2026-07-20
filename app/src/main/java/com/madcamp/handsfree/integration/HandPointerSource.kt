package com.madcamp.handsfree.integration

import com.madcamp.handsfree.tracking.HandTracker
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.flow.Flow

/**
 * HAND → D 경계 구현. [HandTracker]의 SharedFlow를 D의 [PointerSource]에 그대로 연결한다.
 *
 * A의 [FacePointerSource]와 완전히 대칭이다. HandTracker가 손 위치를 [RawFaceOrientation]의
 * yaw/pitch 슬롯에 이미 실어 두었으므로(계약 재사용) 여기서도 변환이 없다.
 *
 * D는 이 PointerSource가 얼굴에서 왔는지 손에서 왔는지 모른다 — 그게 포트를 지키는 이유다.
 */
class HandPointerSource(
    private val tracker: HandTracker,
) : PointerSource {

    override val pointerFrames: Flow<PointerFrame> = tracker.pointerFrames

    override val rawFaceOrientation: Flow<RawFaceOrientation> = tracker.rawOrientations
}
