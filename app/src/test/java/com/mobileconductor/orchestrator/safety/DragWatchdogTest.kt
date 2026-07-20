package com.mobileconductor.orchestrator.safety

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 명세 forD 8절 DoD ⑤ — DRAGGING 30초 초과 시 자동 취소 타이머 동작 검증(가상 시간).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DragWatchdogTest {

    @Test
    fun `fires onTimeout after timeout elapses`() = runTest {
        var fired = 0
        val watchdog = DragWatchdog(backgroundScope, timeoutMs = 30_000L) { fired++ }

        watchdog.start()
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals("타임아웃 전에는 발화하지 않음", 0, fired)

        advanceTimeBy(2)
        runCurrent()
        assertEquals("타임아웃 경과 시 1회 발화", 1, fired)
    }

    @Test
    fun `cancel prevents onTimeout`() = runTest {
        var fired = 0
        val watchdog = DragWatchdog(backgroundScope, timeoutMs = 30_000L) { fired++ }

        watchdog.start()
        advanceTimeBy(10_000)
        runCurrent()
        watchdog.cancel()

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, fired)
    }

    @Test
    fun `restart resets the timer`() = runTest {
        var fired = 0
        val watchdog = DragWatchdog(backgroundScope, timeoutMs = 30_000L) { fired++ }

        watchdog.start()
        advanceTimeBy(20_000)
        runCurrent()
        watchdog.start() // 리셋

        advanceTimeBy(20_000) // 누적 40s지만 리셋 후 20s → 아직
        runCurrent()
        assertEquals(0, fired)

        advanceTimeBy(10_001) // 리셋 후 30s 경과
        runCurrent()
        assertEquals(1, fired)
    }
}
