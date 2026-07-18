package com.mobileconductor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mobileconductor.core.model.CommandId
import com.mobileconductor.core.model.ControllerState
import com.mobileconductor.overlay.ClickFeedback
import com.mobileconductor.overlay.OverlayBus
import com.mobileconductor.overlay.OverlayService
import kotlinx.coroutines.launch

/**
 * D 모듈 개발용 디버그 진입점 (P2).
 *
 * 17개 [CommandId] 버튼으로 더미 음성 명령을 주입하고, 현재 [ControllerState]와
 * 게이트 처리 결과(실행/폐기) 로그를 실시간으로 확인한다. 상태별 유효성 표대로
 * ExecutionCommand가 생성/폐기되는지 눈으로 검증하기 위한 화면이다.
 */
class DebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val harness = DebugHarness(lifecycleScope)

        // LOCKED 수동 해제 버튼 → UNLOCK 주입
        OverlayBus.onManualUnlock = { harness.voice.inject(CommandId.UNLOCK) }

        // Orchestrator 상태/포인터/클릭을 오버레이 버스로 흘려보냄
        lifecycleScope.launch { harness.orchestrator.state.collect { OverlayBus.publishState(it) } }
        lifecycleScope.launch { harness.orchestrator.pointerFrames.collect { OverlayBus.publishPointer(it) } }
        lifecycleScope.launch {
            harness.orchestrator.executionResults.collect {
                if (it.success) {
                    OverlayBus.pointer.value?.let { p -> OverlayBus.publishClick(ClickFeedback(p.x, p.y)) }
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugScreen(harness, onToggleOverlay = ::toggleOverlay)
                }
            }
        }
    }

    /** 오버레이 권한이 있으면 서비스 시작, 없으면 권한 설정 화면으로 유도. */
    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            )
            return
        }
        OverlayService.start(this)
    }
}

@Composable
private fun DebugScreen(harness: DebugHarness, onToggleOverlay: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by harness.orchestrator.state.collectAsStateWithLifecycle()
    val log = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        harness.orchestrator.executionResults.collect {
            log.prepend("→ C 실행: ${it.commandId} (success=${it.success})")
        }
    }
    LaunchedEffect(Unit) {
        harness.orchestrator.rejections.collect {
            log.prepend("✕ 폐기: ${it.name}")
        }
    }
    LaunchedEffect(Unit) {
        harness.orchestrator.notices.collect {
            log.prepend("⚠ 안내: ${it.name}")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        StateBanner(state)

        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggleOverlay) { Text("오버레이 시작/권한") }
            Button(onClick = { OverlayService.stop(context) }) { Text("오버레이 중지") }
        }

        Text(
            "명령 주입 (B 흉내)",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(CommandId.values()) { cmd ->
                Button(
                    onClick = { harness.voice.inject(cmd) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(cmd.name, fontSize = 10.sp)
                }
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            Text("처리 로그", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { log.clear() }) { Text("clear") }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(log) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StateBanner(state: ControllerState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "STATE: $state",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 최신 항목을 위로 쌓고 200개로 제한. */
private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.prepend(item: T) {
    add(0, item)
    if (size > 200) removeAt(lastIndex)
}
