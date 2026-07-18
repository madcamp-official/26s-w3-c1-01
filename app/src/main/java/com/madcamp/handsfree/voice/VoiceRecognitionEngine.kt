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

    private fun listenOnce() {
        if (!isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
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

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle) = handleResults(results)
        override fun onError(error: Int) = scheduleRestart()

        override fun onReadyForSpeech(params: Bundle?) {}
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
    }
}
