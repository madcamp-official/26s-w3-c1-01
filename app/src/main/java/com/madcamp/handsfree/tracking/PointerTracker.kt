package com.madcamp.handsfree.tracking

import androidx.lifecycle.LifecycleOwner
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.core.model.TrackerError
import kotlinx.coroutines.flow.SharedFlow

/**
 * 포인터 입력원 트래커의 공통 계약. [FaceTracker](FACE)와 [HandTracker](HAND)가 구현한다.
 *
 * 통합 레이어([com.madcamp.handsfree.integration])가 얼굴/손을 구분하지 않고 배선할 수
 * 있게 하려고 뽑았다. **모드 전환(Phase 3)이 이 인터페이스 하나만 갈아끼우면 되게 하는
 * 것이 목적**이고, D의 [com.mobileconductor.orchestrator.port.PointerSource]와는 다른
 * 계층이다(이건 A/HAND쪽 내부 계약, 저건 D 경계).
 *
 * 두 구현 모두 상태(ACTIVE/PAUSED/LOCKED)를 모른다 — 방출을 거르는 것은 D의 몫이다.
 */
interface PointerTracker {
    /** 매 프레임 포인터 좌표. 소비가 느리면 오래된 값은 버린다(DROP_OLDEST). */
    val pointerFrames: SharedFlow<PointerFrame>

    /** 캘리브레이션 전용 원시 신호. 프로파일이 없어도 방출된다. */
    val rawOrientations: SharedFlow<RawFaceOrientation>

    /** 권한 거부·모델 로딩 실패 등 프레임을 만들 수 없는 상황. */
    val errors: SharedFlow<TrackerError>

    /** 기기 회전. 트래커가 흡수하므로 소비 측은 가로/세로를 신경 쓰지 않는다. */
    var displayRotationDegrees: Int

    /** 카메라를 열고 추적을 시작한다. 권한이 이미 허용된 뒤 호출해야 한다. */
    fun start(lifecycleOwner: LifecycleOwner)

    /** 카메라·랜드마커를 닫는다. 모드 전환 시 이전 트래커를 멈추는 데 쓴다. */
    fun stop()

    /** 캘리브레이션 완료(또는 재보정) 프로파일을 반영한다. */
    fun updateProfile(profile: CalibrationProfile)
}
