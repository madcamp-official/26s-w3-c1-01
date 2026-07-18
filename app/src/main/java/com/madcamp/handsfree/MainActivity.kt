package com.madcamp.handsfree

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.madcamp.handsfree.databinding.ActivityMainBinding
import com.madcamp.handsfree.voice.VoiceCommandEvent
import com.madcamp.handsfree.voice.VoiceCommandListener
import com.madcamp.handsfree.voice.VoiceEngineError
import com.madcamp.handsfree.voice.VoiceRecognitionEngine

/**
 * Part B 동작 확인용 데모 화면.
 * 완료 기준(spec §6): "콘솔에 VoiceCommandEvent JSON을 출력" — Logcat과 화면 로그에 동시에 남긴다.
 */
class MainActivity : AppCompatActivity(), VoiceCommandListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: VoiceRecognitionEngine
    private var isListening = false

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) engine.start() else onVoiceEngineError(VoiceEngineError.MicPermissionError())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = VoiceRecognitionEngine(applicationContext, this)

        binding.toggleButton.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }
    }

    private fun startListening() {
        isListening = true
        binding.toggleButton.setText(R.string.stop_listening)
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun stopListening() {
        isListening = false
        binding.toggleButton.setText(R.string.start_listening)
        engine.stop()
    }

    override fun onVoiceCommand(event: VoiceCommandEvent) {
        val line = event.toJson()
        Log.d(TAG, line)
        appendLog(line)
    }

    override fun onVoiceEngineError(error: VoiceEngineError) {
        Log.w(TAG, error.toString())
        appendLog(error.toString())
        isListening = false
        binding.toggleButton.setText(R.string.start_listening)
    }

    override fun onUnrecognizedSpeech(rawText: String) {
        Log.d(TAG, "UnrecognizedSpeech: $rawText")
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            binding.logTextView.append("\n$line")
        }
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoiceEngine[PartB]"
    }
}
