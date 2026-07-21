package com.madcamp.handsfree.tracking

import com.mobileconductor.core.model.PointerFrame
import kotlin.math.hypot

/**
 * Post-mapping pointer stabilizer for touch targeting.
 *
 * PointerMapper already smooths raw face motion, but touch UX needs a second layer:
 * tiny head jitter should stop moving the visible pointer, and a point held briefly
 * should stay fixed while the user says "touch".
 */
class PointerStabilizer(
    private val config: Config = Config()
) {
    data class Config(
        val slowAlpha: Float = 0.16f,
        val fastAlpha: Float = 0.42f,
        val fastMoveRadius: Float = 0.045f,
        val deadZoneRadius: Float = 0.008f,
        val dwellRadius: Float = 0.018f,
        val dwellLockMs: Long = 320L,
        val lockBreakRadius: Float = 0.055f,
    )

    private var outputX: Float? = null
    private var outputY: Float? = null
    private var dwellAnchorX: Float? = null
    private var dwellAnchorY: Float? = null
    private var dwellStartedAt: Long = 0L
    private var lockedX: Float? = null
    private var lockedY: Float? = null

    fun reset() {
        outputX = null
        outputY = null
        dwellAnchorX = null
        dwellAnchorY = null
        dwellStartedAt = 0L
        lockedX = null
        lockedY = null
    }

    fun stabilize(frame: PointerFrame): PointerFrame {
        if (!frame.faceDetected) {
            reset()
            return frame
        }

        val lockX = lockedX
        val lockY = lockedY
        if (lockX != null && lockY != null) {
            if (distance(frame.x, frame.y, lockX, lockY) <= config.lockBreakRadius) {
                return frame.copy(x = lockX, y = lockY)
            }
            lockedX = null
            lockedY = null
            dwellAnchorX = frame.x
            dwellAnchorY = frame.y
            dwellStartedAt = frame.timestamp
        }

        val prevX = outputX
        val prevY = outputY
        if (prevX == null || prevY == null) {
            outputX = frame.x
            outputY = frame.y
            dwellAnchorX = frame.x
            dwellAnchorY = frame.y
            dwellStartedAt = frame.timestamp
            return frame
        }

        val delta = distance(frame.x, frame.y, prevX, prevY)
        val alpha = if (delta >= config.fastMoveRadius) config.fastAlpha else config.slowAlpha
        val candidateX = prevX + (frame.x - prevX) * alpha
        val candidateY = prevY + (frame.y - prevY) * alpha

        if (distance(candidateX, candidateY, prevX, prevY) >= config.deadZoneRadius) {
            outputX = candidateX
            outputY = candidateY
        }

        updateDwell(frame)
        return frame.copy(x = outputX ?: frame.x, y = outputY ?: frame.y)
    }

    private fun updateDwell(frame: PointerFrame) {
        val anchorX = dwellAnchorX
        val anchorY = dwellAnchorY
        if (anchorX == null || anchorY == null) {
            dwellAnchorX = frame.x
            dwellAnchorY = frame.y
            dwellStartedAt = frame.timestamp
            return
        }

        if (distance(frame.x, frame.y, anchorX, anchorY) > config.dwellRadius) {
            dwellAnchorX = frame.x
            dwellAnchorY = frame.y
            dwellStartedAt = frame.timestamp
            return
        }

        if (frame.timestamp - dwellStartedAt >= config.dwellLockMs) {
            lockedX = outputX
            lockedY = outputY
        }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return hypot((ax - bx).toDouble(), (ay - by).toDouble()).toFloat()
    }
}
