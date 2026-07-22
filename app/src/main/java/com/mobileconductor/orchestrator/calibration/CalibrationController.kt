package com.mobileconductor.orchestrator.calibration

import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.FaceOrientationValue
import com.mobileconductor.core.model.Level
import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** 5단계에서 사용자가 선택하는 민감도/스무딩 값. */
data class Adjustment(
    val sensitivity: Level = Level.MID,
    val smoothing: Level = Level.MID,
)

/** 캘리브레이션 튜닝 파라미터. */
data class CalibrationConfig(
    /**
     * 캘리브레이션 시작 전 사용법 안내를 보여주는 시간.
     *
     * 사용자가 눈만 굴려 기준점을 따라가는 경우가 많아서, 실제 수집 전에
     * "기기를 고정하고 고개를 돌려 점을 따라가라"는 안내를 먼저 노출한다.
     * (기존 2.5초는 읽기에 짧다는 피드백이 있어 5초로 늘렸다.)
     */
    val introDelayMs: Long = 5_000L,
    /**
     * 기준점 안내가 뜬 뒤 수집을 시작하기까지의 대기 시간.
     *
     * **0으로 두면 캘리브레이션이 통째로 망가진다.** 이 값이 없던 최초 통합 버전에서는
     * 9개 지점을 6초 만에 지나가 버려서, 사용자가 고개를 옮길 새가 없었다. 결과적으로
     * 9개 기준점에 거의 같은 각도가 기록되고 faceRange가 정상의 1/3~1/6로 좁아져서
     * 포인터가 3~6배 과민해졌다(실기기 로그: yaw 범위 16도, pitch 6도).
     *
     * Mock 좌표로는 드러날 수 없는 문제였다 — 사람이 따라올 수 있는 속도인지는
     * 실기기에서만 확인된다.
     */
    val settleDelayMs: Long = 1_500L,
    /**
     * 한 기준점을 확정하기 위해 평균낼 최소 유효(faceDetected) 샘플 수.
     *
     * 15fps 안팎이라 15개면 약 1초다. 10개(0.7초)는 "수집 중"이라는 게
     * 사용자에게 인지되지 않는 길이였다.
     */
    val samplesPerPoint: Int = 15,
    /** 한 기준점에서 유효 샘플이 이만큼 안 모이면 실패로 간주(명세: 5초). */
    val noFaceTimeoutMs: Long = 5_000L,
    /** 한 기준점 최대 재시도. 초과 시 처음부터 재시작(명세: 3회). */
    val maxRetriesPerStep: Int = 3,
    /** 재시작 안내를 오버레이에 노출하는 최소 시간. 이 시간 동안 RESTARTING 상태를 유지한다. */
    val restartAnnounceDelayMs: Long = 1_500L,
)

/**
 * 캘리브레이션 플로우 (FR-006 / 명세 forD 3절).
 *
 * A의 [PointerSource.rawFaceOrientation]을 구독해 9개 기준점 데이터를 순차 수집하고,
 * min/max로 얼굴 범위를 산출한 뒤 [CalibrationProfile]을 만들어 [CalibrationConsumer]에 전달한다.
 *
 * 예외 처리(명세 forD 3절):
 * - 기준점에서 유효 샘플이 [CalibrationConfig.noFaceTimeoutMs] 내에 안 모이면 그 단계를 재시도한다.
 * - 한 단계가 [CalibrationConfig.maxRetriesPerStep]회 실패하면 처음부터 재시작한다.
 * - 중도 취소(코루틴 cancel) 시 임시 저장 없이 폐기된다(프로파일 미생성).
 *
 * 상태(CALIBRATING/ACTIVE) 전이 자체는 Orchestrator/StateHolder가 담당하며, 이 컨트롤러는
 * 수집 플로우와 [uiState]만 책임진다.
 */
class CalibrationController(
    private val source: PointerSource,
    private val consumer: CalibrationConsumer,
    private val config: CalibrationConfig = CalibrationConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _uiState = MutableStateFlow(CalibrationUiState.INITIAL)

    /** 오버레이가 구독하는 진행 상태. */
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    /**
     * 캘리브레이션을 실행한다. 성공 시 생성된 프로파일을 반환하고 [consumer]에 전달한다.
     * 실패한 단계는 내부적으로 재시도/재시작하며, 성공할 때까지(또는 취소될 때까지) 진행한다.
     *
     * @param profileId 프로파일 식별자
     * @param awaitAdjustment 5단계: 사용자의 민감도/스무딩 선택을 기다리는 suspend 훅
     *   (실제 UI에서는 사용자 입력까지 suspend, 테스트/기본값은 즉시 MID/MID)
     */
    suspend fun run(
        profileId: String,
        awaitAdjustment: suspend () -> Adjustment = { Adjustment() },
    ): CalibrationProfile {
        _uiState.value = CalibrationUiState.INITIAL
        delay(config.introDelayMs)
        while (true) {
            val collected = collectAllPoints()
            if (collected == null) {
                // 3회 실패 → 재시작 안내를 일정 시간 노출한 뒤 처음부터 재시작
                _uiState.value = _uiState.value.copy(phase = CalibrationPhase.RESTARTING)
                delay(config.restartAnnounceDelayMs)
                continue
            }

            _uiState.value = CalibrationUiState(
                stepIndex = CalibrationPoint.ordered.lastIndex,
                currentPoint = CalibrationPoint.ordered.last(),
                progress = 1f,
                retryCountForStep = 0,
                phase = CalibrationPhase.ADJUSTING,
            )
            val adjustment = awaitAdjustment()

            val profile = buildProfile(profileId, collected, adjustment)
            consumer.onProfileReady(profile)
            _uiState.value = _uiState.value.copy(phase = CalibrationPhase.DONE)
            return profile
        }
    }

    /** 9개 기준점을 순차 수집. 어느 단계가 [maxRetriesPerStep]회 실패하면 null(재시작 신호). */
    private suspend fun collectAllPoints(): List<FaceOrientationValue>? {
        val points = CalibrationPoint.ordered
        val collected = ArrayList<FaceOrientationValue>(points.size)

        for ((index, point) in points.withIndex()) {
            var failures = 0
            var value: FaceOrientationValue? = null
            while (value == null) {
                _uiState.value = CalibrationUiState(
                    stepIndex = index,
                    currentPoint = point,
                    progress = collected.size.toFloat() / points.size,
                    retryCountForStep = failures,
                    phase = CalibrationPhase.COLLECTING,
                )
                // 안내를 먼저 띄우고 고개를 옮길 시간을 준 뒤에 수집을 시작한다.
                // 이 delay를 collectPoint() 안(=noFaceTimeoutMs 타임아웃 안)에 넣으면
                // 대기 시간이 실패 판정 시간을 잡아먹는다.
                delay(config.settleDelayMs)
                value = collectPoint()
                if (value == null) {
                    failures++
                    if (failures >= config.maxRetriesPerStep) return null
                }
            }
            collected += value
        }
        return collected
    }

    /**
     * 한 기준점 수집: 유효 샘플 [samplesPerPoint]개를 [noFaceTimeoutMs] 내에 모아 평균낸다.
     * 시간 내에 못 모으면 null(단계 실패).
     */
    private suspend fun collectPoint(): FaceOrientationValue? {
        val samples = withTimeoutOrNull(config.noFaceTimeoutMs) {
            source.rawFaceOrientation
                .filter { it.faceDetected }
                .take(config.samplesPerPoint)
                .toList()
        }
        if (samples == null || samples.size < config.samplesPerPoint) return null
        return FaceOrientationValue(
            yaw = samples.map { it.yaw }.average().toFloat(),
            pitch = samples.map { it.pitch }.average().toFloat(),
        )
    }

    private fun buildProfile(
        profileId: String,
        points: List<FaceOrientationValue>,
        adjustment: Adjustment,
    ): CalibrationProfile {
        val now = Instant.ofEpochMilli(clock()).toString()
        return CalibrationProfile(
            profileId = profileId,
            referencePoints = points,
            faceRangeYawMin = points.minOf { it.yaw },
            faceRangeYawMax = points.maxOf { it.yaw },
            faceRangePitchMin = points.minOf { it.pitch },
            faceRangePitchMax = points.maxOf { it.pitch },
            sensitivityLevel = adjustment.sensitivity,
            smoothingLevel = adjustment.smoothing,
            createdAt = now,
            updatedAt = now,
        )
    }
}
