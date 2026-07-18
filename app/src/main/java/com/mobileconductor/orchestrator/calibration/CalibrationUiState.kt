package com.mobileconductor.orchestrator.calibration

/** 캘리브레이션 진행 단계. 오버레이가 이 값으로 안내 UI를 전환한다. */
enum class CalibrationPhase {
    /** 1단계: 기기 거치 및 얼굴 정렬 안내. */
    ALIGNING,

    /** 2~4단계: 현재 기준점 응시 데이터 수집 중. */
    COLLECTING,

    /** 5단계: 테스트 포인터로 민감도/스무딩 조정 중. */
    ADJUSTING,

    /** 9점 수집 실패(3회)로 처음부터 재시작. */
    RESTARTING,

    /** 프로파일 생성 완료. */
    DONE,
}

/**
 * 캘리브레이션 UI 상태 스냅샷 (명세 forD 3절). 오버레이가 구독해 진행률/기준점을 그린다.
 *
 * @param stepIndex 현재 기준점 인덱스 (0..8)
 * @param currentPoint 현재 응시해야 할 기준점
 * @param progress 수집 완료 비율 (0.0 ~ 1.0)
 * @param retryCountForStep 현재 단계에서 누적된 실패 횟수 (0..maxRetries)
 * @param phase 진행 단계
 */
data class CalibrationUiState(
    val stepIndex: Int,
    val currentPoint: CalibrationPoint,
    val progress: Float,
    val retryCountForStep: Int,
    val phase: CalibrationPhase,
) {
    companion object {
        val INITIAL = CalibrationUiState(
            stepIndex = 0,
            currentPoint = CalibrationPoint.CENTER,
            progress = 0f,
            retryCountForStep = 0,
            phase = CalibrationPhase.ALIGNING,
        )
    }
}
