package com.mobileconductor.mock

import com.mobileconductor.core.model.PointerFrame
import com.mobileconductor.core.model.RawFaceOrientation
import com.mobileconductor.orchestrator.port.PointerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 대체 Mock (명세 forD 7절).
 *
 * 포인터는 화면 중앙 주변을 도는 원 궤도로 반복 emit하여 오버레이 렌더를 눈으로 확인할 수 있게 한다.
 * 캘리브레이션용 [rawFaceOrientation]은 완만히 변하는 더미 yaw/pitch를 emit한다.
 *
 * @param periodMs emit 주기(ms)
 * @param clock 타임스탬프 소스(테스트 주입용)
 */
class MockPointerSource(
    private val periodMs: Long = 100L,
    private val clock: () -> Long = System::currentTimeMillis,
) : PointerSource {

    override val pointerFrames: Flow<PointerFrame> = flow {
        var t = 0.0
        while (true) {
            emit(
                PointerFrame(
                    x = (0.5 + 0.25 * cos(t)).toFloat(),
                    y = (0.5 + 0.25 * sin(t)).toFloat(),
                    faceDetected = true,
                    confidence = 0.95f,
                    timestamp = clock(),
                )
            )
            t += 0.15
            delay(periodMs)
        }
    }

    override val rawFaceOrientation: Flow<RawFaceOrientation> = flow {
        var t = 0.0
        while (true) {
            emit(
                RawFaceOrientation(
                    yaw = (15.0 * sin(t)).toFloat(),
                    pitch = (10.0 * cos(t)).toFloat(),
                    faceDetected = true,
                    confidence = 0.95f,
                    timestamp = clock(),
                )
            )
            t += 0.1
            delay(periodMs)
        }
    }
}
