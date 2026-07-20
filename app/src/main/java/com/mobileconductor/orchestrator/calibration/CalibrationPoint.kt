package com.mobileconductor.orchestrator.calibration

/**
 * 캘리브레이션 9개 기준점 (명세 forD 3절: 중앙 + 상/하/좌/우 + 대각선 4).
 *
 * 순서대로 진행하며, [screenX]/[screenY]는 오버레이가 기준점을 하이라이트할 정규화 화면 좌표(0~1)다.
 */
enum class CalibrationPoint(val screenX: Float, val screenY: Float) {
    CENTER(0.5f, 0.5f),
    UP(0.5f, 0.1f),
    DOWN(0.5f, 0.9f),
    LEFT(0.1f, 0.5f),
    RIGHT(0.9f, 0.5f),
    UP_LEFT(0.1f, 0.1f),
    UP_RIGHT(0.9f, 0.1f),
    DOWN_LEFT(0.1f, 0.9f),
    DOWN_RIGHT(0.9f, 0.9f);

    companion object {
        /** 순서대로의 기준점 목록. 크기는 항상 9. */
        val ordered: List<CalibrationPoint> = entries
    }
}
