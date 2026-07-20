package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.madcamp.handsfree.telemetry.Telemetry
import com.madcamp.handsfree.tracking.FaceTracker
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.orchestrator.ConductorContainer
import com.mobileconductor.overlay.ClickFeedback
import com.mobileconductor.overlay.OverlayBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * A/B/C/D 파이프라인의 소유자. **포그라운드 서비스가 켜고 끈다.**
 *
 * 처음에는 이걸 `ControllerActivity`가 들고 있었는데, 그러면 CameraX가 Activity의
 * 라이프사이클에 묶여서 **앱을 나가는 순간 카메라가 끊기고 포인터가 얼어붙는다.**
 * 다른 앱 위에서 쓰는 게 이 앱의 존재 이유라 그건 앱이 성립하지 않는다는 뜻이다.
 *
 * 그래서 수명을 [com.mobileconductor.overlay.OverlayService]에 맞췄다. 오버레이가
 * 떠 있는 동안 카메라·음성·오케스트레이터가 모두 살아 있고, Activity는 권한 확보와
 * 캘리브레이션 트리거만 한다.
 *
 * 프로세스 내 싱글턴인 이유는 서비스와 Activity가 같은 파이프라인을 봐야 하기
 * 때문이다. D가 [OverlayBus]에서 이미 쓴 방식과 같다.
 */
object ControllerPipeline {

    private var tracker: FaceTracker? = null
    private var deps: RealConductorDependencies? = null
    private var container: ConductorContainer? = null
    private var calibrationJob: Job? = null
    private var appContext: Context? = null

    /** 서비스의 lifecycleScope. 서비스가 죽으면 여기 붙은 작업도 같이 취소된다. */
    private var scope: CoroutineScope? = null

    /**
     * 서비스가 아직 안 떠 있을 때 들어온 캘리브레이션 요청.
     *
     * `startForegroundService()`는 비동기라 [start]가 아직 안 돌았을 수 있다.
     * 이걸 안 두면 첫 실행에서 [runCalibration]이 조용히 무시되고, 상태가
     * CALIBRATING에 머물러 **모든 음성 명령이 폐기된다.**
     */
    private var calibrationPending = false

    private val _calibrating = MutableStateFlow(false)

    /** 캘리브레이션 진행 여부. Activity가 안내 문구를 바꾸는 데 쓴다. */
    val calibrating: StateFlow<Boolean> = _calibrating.asStateFlow()

    val isRunning: Boolean get() = container != null

    /**
     * 파이프라인을 세우고 카메라/음성을 시작한다.
     *
     * **카메라·마이크 권한이 이미 허용된 뒤에 호출해야 한다.** 서비스에는 권한을
     * 요청할 방법이 없어서 Activity가 먼저 받아둔다.
     */
    fun start(service: LifecycleService) {
        if (container != null) return

        val t = FaceTracker(service)
        val d = RealConductorDependencies(service.applicationContext, service.lifecycleScope, t)
        val c = ConductorContainer(
            deps = d,
            scope = service.lifecycleScope,
            // 캘리브레이션이 끝나기 전에는 명령을 받지 않는다(FR-006)
            initialState = ControllerState.CALIBRATING,
        )
        tracker = t
        deps = d
        container = c
        scope = service.lifecycleScope
        appContext = service.applicationContext

        // 서비스의 라이프사이클에 바인딩된다 — 앱을 나가도 살아 있다
        t.start(service)
        d.voice.start()

        wireOverlay(service, c, d)
        logBuildStamp(service)
        Log.i(TAG, "파이프라인 시작 (서비스 수명)")

        // 저장된 프로파일이 있으면 보정을 건너뛰고 바로 쓸 수 있게 한다.
        // FR-006이 막으려는 건 "보정 안 된 채 ACTIVE 진입"이지 "이미 보정된 사용자에게
        // 매번 다시 시키는 것"이 아니다.
        val saved = CalibrationStore.load(service.applicationContext)
        if (saved != null) {
            Log.i(TAG, "저장된 프로파일 복원 — ${saved.profileId}")
            t.updateProfile(saved)
            c.orchestrator.onCalibrationComplete()
        }

        if (calibrationPending) {
            calibrationPending = false
            runCalibration()
        }
    }

    /**
     * 지금 돌고 있는 APK가 언제 설치된 것인지 남긴다.
     *
     * **"코드를 고쳤는데 동작이 그대로"일 때 재빌드 여부를 먼저 확인해야 한다.**
     * 실제로 이걸 몰라서 멀쩡한 코드를 의심하며 한 사이클을 날렸다. 설치 시각이
     * 방금이 아니면 폰에 있는 건 옛날 APK다.
     */
    private fun logBuildStamp(context: Context) {
        val installedAt = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrNull() ?: return
        val elapsed = (System.currentTimeMillis() - installedAt) / 1000
        Log.i(TAG, "APK 설치 시각: ${Date(installedAt)} (${elapsed}초 전)")
    }

    private fun wireOverlay(
        service: LifecycleService,
        c: ConductorContainer,
        d: RealConductorDependencies,
    ) {
        val orchestrator = c.orchestrator
        val telemetryLogger = Telemetry.logger(service.applicationContext)

        // 음성 인식이 반복 실패해도 앱에서 빠져나올 수 있어야 한다(FR-005)
        OverlayBus.onManualUnlock = { d.voice.inject(CommandId.UNLOCK) }

        // 제어 명령(STOP/RESUME/LOCK/UNLOCK)은 상태만 바꾸고 C로 가지 않아서
        // 실행 결과도 폐기 사유도 남지 않는다. 즉 상태 전이를 안 찍으면
        // **"잠금"이 먹었는지 확인할 방법이 아예 없다.**
        service.lifecycleScope.launch {
            orchestrator.state.collect { state ->
                Log.i(TAG, "상태 → $state")
                OverlayBus.publishState(state)
            }
        }
        service.lifecycleScope.launch { orchestrator.pointerFrames.collect { OverlayBus.publishPointer(it) } }
        service.lifecycleScope.launch {
            orchestrator.executionResults.collect { result ->
                if (!result.success) return@collect
                // 좌표 없는 명령(BACK 등)은 클릭 애니메이션을 띄우지 않는다
                val x = result.x ?: return@collect
                val y = result.y ?: return@collect
                OverlayBus.publishClick(ClickFeedback(x, y))
            }
        }
        service.lifecycleScope.launch {
            c.calibrationController.uiState.collect { OverlayBus.publishCalibration(it) }
        }

        // 명령이 "인식은 됐는데 아무 일도 안 일어나는" 상황의 원인을 보이게 만든다.
        // D가 이 두 스트림을 노출해 둔 이유가 이건데 통합 때 안 붙여서,
        // 게이트에서 막힌 건지 C에서 실패한 건지 구분할 방법이 없었다.
        service.lifecycleScope.launch {
            orchestrator.rejections.collect { reason ->
                Log.w(TAG, "명령 폐기 — $reason (현재 상태: ${orchestrator.state.value})")
            }
        }
        service.lifecycleScope.launch {
            orchestrator.executionResults.collect { r ->
                if (r.success) {
                    Log.i(TAG, "실행 성공 — ${r.commandId} @ (${r.x}, ${r.y})")
                } else {
                    Log.e(TAG, "실행 실패 — ${r.commandId}: ${r.errorReason}")
                }
            }
        }
        service.lifecycleScope.launch {
            orchestrator.notices.collect { Log.i(TAG, "안내 — $it") }
        }
        service.lifecycleScope.launch {
            d.tracker.errors.collect { error ->
                telemetryLogger.logAppError(
                    type = error.type.name,
                    message = "trackerTimestamp=${error.timestamp}",
                )
            }
        }
    }

    /**
     * 저장된 프로파일이 없을 때만 캘리브레이션을 돌린다. 시작 버튼의 경로다.
     *
     * 서비스가 아직 안 떠 있어도 저장소는 읽을 수 있으므로 [start]를 기다리지 않는다.
     */
    fun runCalibrationIfNeeded(context: Context) {
        if (CalibrationStore.load(context) != null) {
            Log.i(TAG, "저장된 프로파일이 있어 보정을 생략한다")
            return
        }
        runCalibration()
    }

    /**
     * 캘리브레이션을 (재)실행한다. 진행 중이면 이전 것을 취소하고 새로 시작한다 —
     * 두 수집 루프가 같은 좌표 스트림을 나눠 가지면 양쪽 다 표본이 모자라 실패한다.
     */
    fun runCalibration() {
        val c = container
        val s = scope
        if (c == null || s == null) {
            // 서비스가 아직 안 떴다. 떴을 때 이어서 실행한다.
            calibrationPending = true
            Log.i(TAG, "서비스 기동 대기 — 캘리브레이션 예약")
            return
        }
        calibrationJob?.cancel()
        calibrationJob = s.launch {
            val telemetryLogger = appContext?.let { Telemetry.logger(it) }
            _calibrating.value = true
            val started = System.currentTimeMillis()
            Log.i(TAG, "캘리브레이션 시작")
            telemetryLogger?.logCalibrationStarted()
            try {
                c.calibrationController.run(profileId = "default")
                // 프로파일이 A에 주입된 뒤에야 ACTIVE로 보낸다.
                // CALIBRATING에서는 모든 음성 명령이 폐기되므로 명령 주입으로는 못 나간다.
                // 재보정일 때는 이미 ACTIVE라 이 호출이 무시된다(그대로 두는 게 맞다).
                c.orchestrator.onCalibrationComplete()
                OverlayBus.publishCalibration(null)
                val durationMs = System.currentTimeMillis() - started
                telemetryLogger?.logCalibrationCompleted(durationMs)
                Log.i(TAG, "캘리브레이션 완료 — ${durationMs}ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                telemetryLogger?.logCalibrationFailed(e::class.simpleName ?: "CALIBRATION_FAILED")
                telemetryLogger?.logAppError(
                    type = "CALIBRATION_FAILED",
                    message = e.message,
                )
                throw e
            } finally {
                _calibrating.value = false
            }
        }
    }

    /** 기기 회전은 A가 흡수한다. Activity가 각도만 알려주면 C/D는 신경 쓰지 않는다. */
    fun updateRotation(degrees: Int) {
        tracker?.displayRotationDegrees = degrees
    }

    fun stop() {
        calibrationJob?.cancel()
        calibrationJob = null
        tracker?.stop()
        deps?.voice?.stop()
        container?.orchestrator?.stop()
        OverlayBus.onManualUnlock = null
        tracker = null
        deps = null
        container = null
        scope = null
        appContext = null
        _calibrating.value = false
        Log.i(TAG, "파이프라인 정지")
    }

    private const val TAG = "HF-Pipeline"
}
