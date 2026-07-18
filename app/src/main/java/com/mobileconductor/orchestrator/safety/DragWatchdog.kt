package com.mobileconductor.orchestrator.safety

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 드래그 장시간 방치 안전장치 (명세 forD 6절 / FR-003).
 *
 * DRAGGING 진입 시 [start]로 타이머를 켜고, [timeoutMs] 안에 [cancel]되지 않으면 [onTimeout]을
 * 호출한다(Orchestrator가 이를 자동 `DRAG_CANCEL`로 처리). DRAGGING을 벗어나면 [cancel]로 끈다.
 *
 * @param scope 타이머 코루틴을 돌릴 스코프
 * @param timeoutMs 자동 취소까지의 시간(기본 30초)
 * @param onTimeout 타임아웃 시 실행할 동작
 */
class DragWatchdog(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val onTimeout: suspend () -> Unit,
) {
    private var job: Job? = null

    /** 타이머 시작. 이미 돌고 있으면 리셋(재시작). */
    fun start() {
        cancel()
        job = scope.launch {
            delay(timeoutMs)
            onTimeout()
        }
    }

    /** 타이머 해제(DRAGGING 이탈 시). */
    fun cancel() {
        job?.cancel()
        job = null
    }

    val isRunning: Boolean get() = job?.isActive == true

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
