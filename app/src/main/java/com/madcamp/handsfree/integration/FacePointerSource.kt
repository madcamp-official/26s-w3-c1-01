package com.madcamp.handsfree.integration

import com.madcamp.handsfree.tracking.FaceTracker
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.flow.Flow

/**
 * A → D 경계 구현. [FaceTracker]의 SharedFlow를 D의 [PointerSource]에 그대로 연결한다.
 *
 * 변환이 없는 이유는 A/D의 계약 타입을 core.model 한 벌로 합쳤기 때문이다.
 * 합치기 전에는 PointerFrame이 A/C/D 세 벌이었고 필드 순서까지 달랐다.
 */
class FacePointerSource(
    private val tracker: FaceTracker,
) : PointerSource {

    override val pointerFrames: Flow<PointerFrame> = tracker.pointerFrames

    override val rawFaceOrientation: Flow<RawFaceOrientation> = tracker.rawOrientations
}
