package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.PointerFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerStabilizerTest {

    @Test
    fun `tiny jitter stays inside dead zone`() {
        val stabilizer = PointerStabilizer()

        val first = stabilizer.stabilize(frame(x = 0.5f, y = 0.5f, timestamp = 0L))
        val jitter = stabilizer.stabilize(frame(x = 0.504f, y = 0.503f, timestamp = 33L))

        assertEquals(first.x, jitter.x, 0.0001f)
        assertEquals(first.y, jitter.y, 0.0001f)
    }

    @Test
    fun `large movement follows without jumping directly to raw input`() {
        val stabilizer = PointerStabilizer()

        stabilizer.stabilize(frame(x = 0.5f, y = 0.5f, timestamp = 0L))
        val moved = stabilizer.stabilize(frame(x = 0.7f, y = 0.5f, timestamp = 33L))

        assertTrue(moved.x > 0.5f)
        assertTrue(moved.x < 0.7f)
    }

    @Test
    fun `held pointer locks and ignores small movement while touching target`() {
        val stabilizer = PointerStabilizer()

        stabilizer.stabilize(frame(x = 0.5f, y = 0.5f, timestamp = 0L))
        stabilizer.stabilize(frame(x = 0.506f, y = 0.497f, timestamp = 160L))
        val locked = stabilizer.stabilize(frame(x = 0.503f, y = 0.504f, timestamp = 330L))
        val afterLockJitter = stabilizer.stabilize(frame(x = 0.512f, y = 0.508f, timestamp = 360L))

        assertEquals(locked.x, afterLockJitter.x, 0.0001f)
        assertEquals(locked.y, afterLockJitter.y, 0.0001f)
    }

    @Test
    fun `face loss resets stabilization state`() {
        val stabilizer = PointerStabilizer()

        stabilizer.stabilize(frame(x = 0.5f, y = 0.5f, timestamp = 0L))
        stabilizer.stabilize(frame(x = 0.8f, y = 0.8f, timestamp = 33L, faceDetected = false))
        val recovered = stabilizer.stabilize(frame(x = 0.2f, y = 0.2f, timestamp = 66L))

        assertEquals(0.2f, recovered.x, 0.0001f)
        assertEquals(0.2f, recovered.y, 0.0001f)
    }

    private fun frame(
        x: Float,
        y: Float,
        timestamp: Long,
        faceDetected: Boolean = true,
    ): PointerFrame {
        return PointerFrame(
            x = x,
            y = y,
            faceDetected = faceDetected,
            confidence = if (faceDetected) 1f else 0f,
            timestamp = timestamp,
        )
    }
}
