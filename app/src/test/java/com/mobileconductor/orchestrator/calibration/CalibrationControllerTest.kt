package com.mobileconductor.orchestrator.calibration

import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.Level
import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.orchestrator.port.CalibrationConsumer
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 명세 forD 8절 DoD ③ — 캘리브레이션 9단계가 더미 RawFaceOrientation으로 정상 완료되고
 * CalibrationProfile이 스키마대로 생성되는지 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationControllerTest {

    /** faceDetected=true인 고정 yaw/pitch를 주기적으로 emit하는 테스트 소스. */
    private class ConstantFaceSource(
        private val yaw: Float,
        private val pitch: Float,
        private val faceDetected: Boolean = true,
        private val periodMs: Long = 50L,
    ) : PointerSource {
        override val pointerFrames: Flow<PointerFrame> = emptyFlow()
        override val rawFaceOrientation: Flow<RawFaceOrientation> = flow {
            while (true) {
                emit(RawFaceOrientation(yaw, pitch, faceDetected, 0.95f, 0L))
                delay(periodMs)
            }
        }
    }

    private class CapturingConsumer : CalibrationConsumer {
        var profile: CalibrationProfile? = null
        override fun onProfileReady(profile: CalibrationProfile) {
            this.profile = profile
        }
    }

    private val fixedClock = { 1_784_332_800_000L } // 2026-07-18T00:00:00Z (epoch ms)

    @Test
    fun `completes 9 points and produces a schema-valid profile`() = runTest {
        val consumer = CapturingConsumer()
        val controller = CalibrationController(
            source = ConstantFaceSource(yaw = 12.5f, pitch = -4.0f),
            consumer = consumer,
            config = CalibrationConfig(samplesPerPoint = 5),
            clock = fixedClock,
        )

        val profile = controller.run(profileId = "user_001")

        assertEquals("user_001", profile.profileId)
        assertEquals(9, profile.referencePoints.size)
        // 모든 기준점이 동일 값 → 범위 min==max==해당 값
        assertEquals(12.5f, profile.faceRangeYawMin, 1e-3f)
        assertEquals(12.5f, profile.faceRangeYawMax, 1e-3f)
        assertEquals(-4.0f, profile.faceRangePitchMin, 1e-3f)
        assertEquals(-4.0f, profile.faceRangePitchMax, 1e-3f)
        assertEquals("2026-07-18T00:00:00Z", profile.createdAt)
        assertEquals(profile.createdAt, profile.updatedAt)
        // consumer(A)에게 전달됨
        assertEquals(profile, consumer.profile)
        assertEquals(CalibrationPhase.DONE, controller.uiState.value.phase)
    }

    @Test
    fun `applies user-selected sensitivity and smoothing at step 5`() = runTest {
        val consumer = CapturingConsumer()
        val controller = CalibrationController(
            source = ConstantFaceSource(yaw = 0f, pitch = 0f),
            consumer = consumer,
            config = CalibrationConfig(samplesPerPoint = 3),
            clock = fixedClock,
        )

        val profile = controller.run(profileId = "p") {
            Adjustment(sensitivity = Level.HIGH, smoothing = Level.LOW)
        }

        assertEquals(Level.HIGH, profile.sensitivityLevel)
        assertEquals(Level.LOW, profile.smoothingLevel)
    }

    @Test
    fun `restarts from the beginning after 3 failed attempts on a point`() = runTest {
        val consumer = CapturingConsumer()
        // 얼굴이 계속 미검출 → 어느 기준점도 확정 불가 → 3회 실패 후 재시작
        val controller = CalibrationController(
            source = ConstantFaceSource(yaw = 0f, pitch = 0f, faceDetected = false),
            consumer = consumer,
            config = CalibrationConfig(
                introDelayMs = 0L,
                settleDelayMs = 1_000L,
                samplesPerPoint = 5,
                noFaceTimeoutMs = 5_000L,
                maxRetriesPerStep = 3,
            ),
            clock = fixedClock,
        )

        // run은 재시작을 무한 반복하므로 백그라운드에서 돌리고, 3회 실패에 필요한 가상시간을
        // 명시적으로 진행시킨다. first{}로 암묵적 idle-advance에 기대면 backgroundScope
        // 작업이 실제 시간 타임아웃(60s)까지 안 풀리는 경우가 있어 피한다.
        //
        // 한 번의 시도 = settleDelay(안내 후 대기) + noFaceTimeout(수집 실패 판정).
        // settleDelay가 타임아웃 밖에 있다는 것도 이 계산이 함께 고정한다.
        backgroundScope.launch { controller.run(profileId = "p") }
        advanceTimeBy(3 * (1_000L + 5_000L) + 100)
        runCurrent()

        assertEquals(CalibrationPhase.RESTARTING, controller.uiState.value.phase)
        // 재시작 전까지 프로파일은 생성되지 않아야 함
        assertTrue(consumer.profile == null)
    }

    @Test
    fun `progresses through collecting phase before completion`() = runTest {
        val consumer = CapturingConsumer()
        val controller = CalibrationController(
            source = ConstantFaceSource(yaw = 1f, pitch = 1f),
            consumer = consumer,
            config = CalibrationConfig(samplesPerPoint = 3),
            clock = fixedClock,
        )

        val seenPhases = mutableSetOf<CalibrationPhase>()
        backgroundScope.launch { controller.uiState.collect { seenPhases += it.phase } }

        // 조정 단계에서 잠깐 suspend시켜 StateFlow conflation으로 ADJUSTING이 누락되지 않게 함
        controller.run(profileId = "p") {
            delay(1)
            Adjustment()
        }

        assertTrue("수집 단계를 거쳐야 함", CalibrationPhase.COLLECTING in seenPhases)
        assertTrue("조정 단계를 거쳐야 함", CalibrationPhase.ADJUSTING in seenPhases)
        assertNotNull(consumer.profile)
    }
}
