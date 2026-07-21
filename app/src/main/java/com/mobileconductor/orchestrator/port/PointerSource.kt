package com.mobileconductor.orchestrator.port

import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import kotlinx.coroutines.flow.Flow

/**
 * A → D 경계. 포인터 좌표 스트림과 (캘리브레이션용) 얼굴 방향 원시값 스트림을 제공한다.
 *
 * D는 이 인터페이스에만 의존하며, 실제 구현은 A의 FaceTracker 또는 Mock으로 교체된다.
 */
interface PointerSource {
    /** 매 프레임 포인터 좌표(정규화 0~1). ACTIVE 렌더링에 사용. */
    val pointerFrames: Flow<PointerFrame>

    /** 캘리브레이션 중 얼굴 방향 원시값. 9개 기준점 수집에 사용. */
    val rawFaceOrientation: Flow<RawFaceOrientation>
}
