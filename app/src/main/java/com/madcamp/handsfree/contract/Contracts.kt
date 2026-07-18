package com.madcamp.handsfree.contract

/**
 * A/B/C/D 파트 간 계약 타입.
 *
 * 이 파일은 A파트 명세서 §3의 JSON 스키마를 그대로 옮긴 것이다.
 * **필드명을 바꾸면 C(실행 엔진)와 D(오케스트레이터)가 조용히 깨진다.**
 * 통합 전에는 이 파일을 A가 소유하지만, 변경은 반드시 팀에 공지한다.
 *
 * 확정되지 않은 항목과 A가 임시로 정한 값은 docs/PART_A_OPEN_ISSUES.md 참고.
 */

/**
 * A → D (캘리브레이션 전용). 매핑을 거치지 않은 순수 추정치.
 *
 * D의 Calibration Manager가 9개 기준점 각각에서 이 스트림을 구독한다.
 * PointerFrame과 달리 CalibrationProfile이 없어도 방출된다 —
 * 애초에 프로파일을 만들기 위한 데이터라서 그렇다.
 */
data class RawFaceOrientation(
    val timestamp: Long,
    /** 도(degree). +가 사용자 기준 오른쪽. 규약 근거는 OPEN_ISSUES #5 */
    val yaw: Float,
    /** 도(degree). +가 위 */
    val pitch: Float,
    /** 눈동자가 눈 중심에서 벗어난 정도. -1.0 ~ 1.0 정규화 */
    val eyeOffsetX: Float,
    val eyeOffsetY: Float,
    val faceDetected: Boolean,
    /**
     * 얼굴 검출 신뢰도만 의미한다. 저조도는 [PointerFrame.lowLight]로 따로 뺐다.
     * 명세서는 저조도 시 confidence를 낮추라고 했지만 둘은 다른 신호다(OPEN_ISSUES #4).
     */
    val confidence: Float,
)

/**
 * A → C, A → D. 매핑이 끝난 화면 좌표.
 *
 * C는 이 좌표로 탭/드래그 위치를 정하고, D는 이 좌표로 포인터를 그린다.
 */
data class PointerFrame(
    val timestamp: Long,
    /**
     * 화면 대비 정규화 좌표 0.0~1.0. 픽셀 변환은 소비 측(C, D) 책임.
     *
     * **null이 되지 않는다.** 명세서 §4는 프로파일 미수신 시 null을 허용했지만
     * C 코드에 null 분기가 없어서 보내면 터진다. 대신 화면 중앙(0.5)을 보내고
     * faceDetected=false로 알린다 (OPEN_ISSUES #3).
     *
     * 기준은 **현재 회전 상태의 전체 화면**이다. 회전 보정은 A가 흡수하므로
     * 소비 측은 가로/세로를 신경 쓰지 않아도 된다 (OPEN_ISSUES #5).
     */
    val x: Float,
    val y: Float,
    val faceDetected: Boolean,
    val confidence: Float,
    /**
     * 계약에 없는 A 확장 필드. 저조도 안내(D의 UI 정책)를 위해 추가했다.
     * D가 안 쓰면 무시하면 되므로 추가해도 아무것도 깨지지 않는다.
     */
    val lowLight: Boolean = false,
)

/** D → A (읽기 전용 구독). 재보정 시 갱신되면 A는 즉시 매핑을 다시 계산한다. */
data class CalibrationProfile(
    val profileId: String,
    /**
     * 9개 기준점의 원시 방향값.
     *
     * **A는 이 값으로 보간하지 않는다.** faceRange* min/max 선형 정규화만 쓴다.
     * 9점 보간은 정확도는 올라가지만 MVP 비용에 안 맞는다고 판단했다.
     * D가 "보간은 A가 한다"고 기대했다면 통합 때 어긋난다 (OPEN_ISSUES #6).
     */
    val referencePoints: List<ReferencePoint>,
    val faceRangeYawMin: Float,
    val faceRangeYawMax: Float,
    val faceRangePitchMin: Float,
    val faceRangePitchMax: Float,
    val sensitivityLevel: Level,
    val smoothingLevel: Level,
) {
    data class ReferencePoint(val yaw: Float, val pitch: Float)

    /** 명세서의 낮음/중간/높음 3단계. JSON에서는 "low"/"mid"/"high" */
    enum class Level { LOW, MID, HIGH }
}

/**
 * A → D. 권한 거부 등 PointerFrame을 아예 만들 수 없는 상황.
 *
 * 명세서 §3 인터페이스 목록에는 없고 §4 예외 표에만 등장해서 스키마가 없었다.
 * B의 MicPermissionError와 형태를 맞춰야 한다 (OPEN_ISSUES #8).
 */
data class TrackerError(
    val type: Type,
    val timestamp: Long,
) {
    enum class Type { CAMERA_PERMISSION_DENIED, CAMERA_UNAVAILABLE, MODEL_LOAD_FAILED }
}
