package com.madcamp.handsfree.integration

import android.util.Log
import com.madcamp.handsfree.tracking.GestureClassifier
import com.madcamp.handsfree.tracking.HandGesture
import com.madcamp.handsfree.tracking.HandLandmarks
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.VoiceCommandEvent
import com.mobileconductor.orchestrator.port.VoiceCommandSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * HAND → D 경계 구현. 손 제스처를 **음성 명령과 동일한** [VoiceCommandEvent] 스트림으로 바꾼다.
 *
 * D의 CommandGate는 이 이벤트의 출처(음성/제스처)를 모른다 — `CommandId`만 본다.
 * 그래서 [MergedCommandSource]로 음성과 합치면 상태 머신·실행부를 전혀 안 고치고도
 * 제스처가 음성과 똑같이 게이트키핑된다(MOTION_CAPTURE_PLAN §9-B, 포트 무변경).
 *
 * [B의 음성 어댑터][VoiceCommandSourceAdapter]와 대칭 구조다: 콜백/스트림을 SharedFlow로
 * 바꾸고, 신뢰도 임계값 대신 **제스처 게이트**(유지 프레임·쿨다운)로 오검출을 막는다.
 */
class GestureCommandSource(
    private val landmarks: Flow<HandLandmarks>,
    private val classifier: GestureClassifier = GestureClassifier(),
) : VoiceCommandSource {

    private val _events = MutableSharedFlow<VoiceCommandEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<VoiceCommandEvent> = _events.asSharedFlow()

    /** 마지막 발화 시각(단조 시계). 쿨다운 계산 전용 — 벽시계와 섞지 않는다. */
    private var lastFiredElapsed = 0L

    /** 랜드마크 스트림 구독을 시작한다. 스코프는 서비스 수명(카메라와 함께 죽는다). */
    fun start(scope: CoroutineScope) {
        scope.launch {
            landmarks.collect { frame ->
                val gesture = classifier.onFrame(frame) ?: return@collect
                onGesture(gesture, frame.timestamp)
            }
        }
    }

    private fun onGesture(gesture: HandGesture, elapsedTs: Long) {
        val commandId = map(gesture) ?: return

        // 쿨다운: 같은/다른 제스처가 너무 빨리 연달아 발화하는 것을 막는다(SPEC §4.4).
        // 정적은 유지-1회 발화라 자연 쿨다운이 있지만, 스와이프 연타를 여기서 한 번 더 막는다.
        if (elapsedTs - lastFiredElapsed < COOLDOWN_MS) return
        lastFiredElapsed = elapsedTs

        Log.i(TAG, "제스처 $gesture -> $commandId")
        _events.tryEmit(
            VoiceCommandEvent(
                commandId = commandId,
                confidence = 1f,
                // 음성 경로와 같은 벽시계를 쓴다(VoiceCommandEvent.timestamp 규약: epoch millis).
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    /**
     * 제스처 → 명령. Phase 2 매핑(MOTION_CAPTURE_PLAN §2.4).
     * 안전/내비 크리티컬(STOP/LOCK/UNLOCK/BACK)은 제스처에 없다 → null이 될 일도 없지만
     * 나중에 제스처가 늘어도 매핑 없는 것은 무시된다.
     */
    private fun map(gesture: HandGesture): CommandId? = when (gesture) {
        HandGesture.PINCH -> CommandId.TOUCH
        HandGesture.FIST -> CommandId.DRAG_START
        HandGesture.OPEN_PALM -> CommandId.DRAG_END
        HandGesture.THUMBS_UP -> CommandId.RESUME
        HandGesture.SWIPE_UP -> CommandId.SCROLL_UP
        HandGesture.SWIPE_DOWN -> CommandId.SCROLL_DOWN
        HandGesture.SWIPE_LEFT -> CommandId.PREV
        HandGesture.SWIPE_RIGHT -> CommandId.NEXT
    }

    private companion object {
        const val TAG = "HF-Gesture"
        const val COOLDOWN_MS = 800L
    }
}
