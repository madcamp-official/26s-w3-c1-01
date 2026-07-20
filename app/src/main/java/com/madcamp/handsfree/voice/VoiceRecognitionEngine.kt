package com.madcamp.handsfree.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * FR-007 담당 모듈. 마이크를 듣고 정규화된 VoiceCommandEvent를 [listener]로 흘려보내는
 * 완전 독립 센서 모듈 — 컨트롤러 상태(ACTIVE/PAUSED/LOCKED 등)는 전혀 알지 못한다.
 */
class VoiceRecognitionEngine(
    private val context: Context,
    private val listener: VoiceCommandListener,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 온디바이스 인식 사용 여부. **기본을 온라인으로 되돌렸다.**
     *
     * NFR은 "온디바이스·오프라인"(음성 원본을 서버로 보내지 않는다)을 요구하고
     * 오프라인이면 네트워크 왕복이 사라져 반응도 빨라진다. 그래서 기본으로 켜 봤는데,
     * **검증 기기(갤럭시 S7)에서 음성 인식이 통째로 죽었다.** 한국어 오프라인 모델이
     * 없으면 SpeechRecognizer가 에러조차 주지 않고 침묵한다.
     *
     * 동작하지 않는 NFR 준수보다 동작하는 앱이 먼저라 온라인으로 되돌린다.
     * **이건 NFR 미충족 상태이며 팀이 알고 결정해야 하는 항목이다** —
     * docs/INTEGRATION.md에 미해결로 기록해 뒀다.
     *
     * 기기에 오프라인 모델이 있는 게 확인되면 [PREFER_OFFLINE_BY_DEFAULT]를 켜면 된다.
     * 아래 폴백/감시자 경로는 그대로 살아 있다.
     */
    private var useOffline = PREFER_OFFLINE_BY_DEFAULT

    /** 오프라인 상태에서 연속으로 난 하드 에러 수. 인식 성공 시 0으로 돌아간다. */
    private var consecutiveHardErrors = 0

    fun start() {
        if (isListening) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onVoiceEngineError(VoiceEngineError.MicPermissionError())
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onVoiceEngineError(VoiceEngineError.RecognizerUnavailable())
            return
        }

        isListening = true
        // 이전 세션에서 폴백으로 꺼졌을 수 있으니 기본값으로 되돌린다
        useOffline = PREFER_OFFLINE_BY_DEFAULT
        consecutiveHardErrors = 0
        Log.i(TAG, "인식 시작 — ${if (useOffline) "오프라인 우선" else "온라인"}")
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
        listenOnce()
    }

    fun stop() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * 인식기가 응답 자체를 안 할 때 깨우는 감시자.
     *
     * **오프라인 모델이 없는 기기에서 SpeechRecognizer는 에러도 안 주고 조용히 죽는다.**
     * 실기기(갤럭시 S7)에서 `EXTRA_PREFER_OFFLINE`을 켠 뒤 28초 동안 콜백이 하나도
     * 오지 않았다. onError 기반 폴백만 있으면 이 상태를 영원히 못 벗어난다.
     */
    private val unresponsiveWatchdog = Runnable { onRecognizerUnresponsive() }

    private fun onRecognizerUnresponsive() {
        if (!isListening) return
        if (useOffline) {
            useOffline = false
            Log.w(TAG, "오프라인 인식기가 응답하지 않는다(에러조차 없음) — 온라인으로 전환한다")
        } else {
            Log.w(TAG, "인식기가 응답하지 않는다 — 재시작한다")
        }
        // 죽은 세션이 남아 있으면 다음 startListening이 ERROR_BUSY로 막힌다
        speechRecognizer?.cancel()
        scheduleRestart()
    }

    private fun listenOnce() {
        if (!isListening) return
        mainHandler.removeCallbacks(unresponsiveWatchdog)
        mainHandler.postDelayed(unresponsiveWatchdog, UNRESPONSIVE_TIMEOUT_MS)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOffline)
        }
        speechRecognizer?.startListening(intent)
    }

    /** 한 발화가 끝난 뒤 짧은 지연 후 재시작해 연속 청취를 유지한다(ERROR_BUSY 회피). */
    private fun scheduleRestart() {
        if (!isListening) return
        mainHandler.postDelayed({ listenOnce() }, RESTART_DELAY_MS)
    }

    private fun handleResults(results: Bundle) {
        val candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val rawText = candidates?.firstOrNull()?.trim()

        // 결과가 돌아왔다 = 현재 모드가 동작한다. 폴백 카운터를 되돌린다
        consecutiveHardErrors = 0

        if (rawText.isNullOrEmpty()) {
            scheduleRestart()
            return
        }

        // 다수 기기의 STT 엔진이 신뢰도 점수를 채워주지 않는다 — 값이 없으면 통과시킨다(과도한 이벤트 유실 방지).
        val confidence = confidenceScores?.getOrNull(0) ?: 1f
        if (confidence < confidenceThreshold) {
            scheduleRestart()
            return
        }

        val matches = CommandDictionary.matchAll(rawText)
        if (matches.isEmpty()) {
            listener.onUnrecognizedSpeech(rawText)
        } else {
            matches.forEach { match ->
                listener.onVoiceCommand(
                    VoiceCommandEvent(
                        commandId = match.commandId,
                        rawText = match.matchedText,
                        confidence = confidence,
                    )
                )
            }
        }
        scheduleRestart()
    }

    /**
     * 오프라인 모델이 없을 때 나는 에러를 세다가 온라인으로 되돌린다.
     *
     * **ERROR_SPEECH_TIMEOUT과 ERROR_NO_MATCH는 세지 않는다.** 연속 청취라
     * 아무도 말하지 않는 동안 이 둘이 계속 나는데, 이건 "인식기는 멀쩡한데 들은 게
     * 없다"는 뜻이다. 여기까지 세면 조용히 있기만 해도 온라인으로 넘어가 버린다.
     */
    private fun handleError(error: Int) {
        if (useOffline && error in HARD_ERRORS) {
            consecutiveHardErrors++
            // 폴백 전에도 남긴다. 임계값에 도달해야만 찍으면 "인식이 아예 안 되는데
            // 로그도 없는" 구간이 생겨서 원인을 못 찾는다.
            Log.w(TAG, "인식 실패 코드 $error (오프라인 모드, ${consecutiveHardErrors}회째)")
            if (consecutiveHardErrors >= OFFLINE_FALLBACK_THRESHOLD) {
                useOffline = false
                // 한글은 Kotlin에서 식별자로 인정돼서 $변수 뒤에 바로 붙이면
                // 통째로 변수명으로 읽힌다. 중괄호로 끊어야 한다
                Log.w(TAG, "오프라인 인식 실패(${consecutiveHardErrors}회) — 온라인으로 전환한다. 기기에 한국어 오프라인 모델이 없는 것으로 보인다")
            }
        }
        scheduleRestart()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            mainHandler.removeCallbacks(unresponsiveWatchdog)
            handleResults(results)
        }

        override fun onError(error: Int) {
            mainHandler.removeCallbacks(unresponsiveWatchdog)
            handleError(error)
        }

        /** 인식기가 살아 있다는 유일한 확실한 신호. 감시자를 해제한다 */
        override fun onReadyForSpeech(params: Bundle?) {
            mainHandler.removeCallbacks(unresponsiveWatchdog)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6f
        private const val RESTART_DELAY_MS = 300L
        // 대괄호를 쓰면 안 된다 — Logcat 필터에서 정규식 메타문자로 해석돼
        // tag:VoiceEngine[PartB]로 걸면 아무것도 안 잡힌다
        private const val TAG = "HF-Voice"

        /**
         * 오프라인(온디바이스) 인식을 기본으로 쓸지.
         *
         * 검증 기기에 한국어 오프라인 모델이 없어 꺼 뒀다. NFR 미충족 상태이며
         * 팀 결정이 필요한 항목이다(docs/INTEGRATION.md).
         */
        private const val PREFER_OFFLINE_BY_DEFAULT = false

        /** 이만큼 연속 실패하면 온라인으로 되돌린다 */
        private const val OFFLINE_FALLBACK_THRESHOLD = 3

        /**
         * startListening 후 이 시간 안에 onReadyForSpeech가 안 오면 죽은 것으로 본다.
         * 정상이면 1초 안에 온다 — 5초는 느린 기기까지 감안한 여유값이다.
         */
        private const val UNRESPONSIVE_TIMEOUT_MS = 5_000L

        /**
         * "오프라인 모델이 없다"로 해석할 에러들.
         *
         * 오프라인 모드인데 네트워크 에러가 난다는 건 인식기가 온디바이스로 처리하지
         * 못하고 서버로 가려다 실패했다는 뜻이라 여기 포함한다.
         */
        private val HARD_ERRORS = setOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT,
        )
    }
}
