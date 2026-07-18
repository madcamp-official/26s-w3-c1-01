package com.example.hands_free_controller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hands_free_controller.input.CommandId
import com.example.hands_free_controller.input.ExecutionCommand
import com.example.hands_free_controller.input.ExecutionResult
import com.example.hands_free_controller.input.InputExecutionEngine
import com.example.hands_free_controller.input.PointerFrame
import com.example.hands_free_controller.service.GestureAccessibilityService
import com.example.hands_free_controller.ui.theme.Hands_free_controllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Hands_free_controllerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MockInputTestPanel(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MockInputTestPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember { InputExecutionEngine(context.applicationContext) }

    var pointer by remember {
        mutableStateOf(
            PointerFrame(
                timestamp = System.currentTimeMillis(),
                x = 0.5f,
                y = 0.5f,
                faceDetected = true,
                confidence = 1f
            )
        )
    }
    var lastResult by remember { mutableStateOf("No command executed yet") }
    var serviceConnected by remember {
        mutableStateOf(GestureAccessibilityService.instance != null)
    }

    LaunchedEffect(Unit) {
        engine.updatePointerFrame(pointer)
        serviceConnected = GestureAccessibilityService.instance != null
    }

    fun updatePointer(x: Float, y: Float, faceDetected: Boolean = true) {
        val frame = PointerFrame(
            timestamp = System.currentTimeMillis(),
            x = x,
            y = y,
            faceDetected = faceDetected,
            confidence = if (faceDetected) 1f else 0f
        )
        pointer = frame
        engine.updatePointerFrame(frame)
    }

    fun execute(commandId: CommandId) {
        serviceConnected = GestureAccessibilityService.instance != null
        engine.execute(ExecutionCommand(commandId)) { result ->
            serviceConnected = GestureAccessibilityService.instance != null
            lastResult = result.toDisplayText()
        }
    }

    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "C Engine Mock Test",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Pointer x=${pointer.x.format2()}, y=${pointer.y.format2()}, faceDetected=${pointer.faceDetected}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Accessibility service: ${if (serviceConnected) "connected" else "not connected"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (serviceConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) {
                    Text("Accessibility Settings")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        serviceConnected = GestureAccessibilityService.instance != null
                    }
                ) {
                    Text("Refresh Status")
                }
            }

            PointerPreview(pointer = pointer)

            Text(
                text = "Mock A: PointerFrame",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ButtonGrid {
                TestButton("Center") { updatePointer(0.5f, 0.5f) }
                TestButton("Top") { updatePointer(0.5f, 0.2f) }
                TestButton("Bottom") { updatePointer(0.5f, 0.8f) }
                TestButton("Left") { updatePointer(0.2f, 0.5f) }
                TestButton("Right") { updatePointer(0.8f, 0.5f) }
                TestButton("Face Lost") {
                    updatePointer(pointer.x, pointer.y, faceDetected = false)
                }
            }

            Text(
                text = "Mock D: ExecutionCommand",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ButtonGrid {
                TestButton("TOUCH") { execute(CommandId.TOUCH) }
                TestButton("BACK") { execute(CommandId.BACK) }
                TestButton("DOWN") { execute(CommandId.SCROLL_DOWN) }
                TestButton("UP") { execute(CommandId.SCROLL_UP) }
                TestButton("SMALL DOWN") { execute(CommandId.SCROLL_DOWN_SMALL) }
                TestButton("LARGE DOWN") { execute(CommandId.SCROLL_DOWN_LARGE) }
                TestButton("NEXT") { execute(CommandId.NEXT) }
                TestButton("PREV") { execute(CommandId.PREV) }
            }

            Text(
                text = "Mock Drag",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ButtonGrid {
                TestButton("Drag Start") { execute(CommandId.DRAG_START) }
                TestButton("Move Down") {
                    updatePointer(pointer.x, (pointer.y + 0.12f).coerceAtMost(0.9f))
                }
                TestButton("Move Up") {
                    updatePointer(pointer.x, (pointer.y - 0.12f).coerceAtLeast(0.1f))
                }
                TestButton("Drag End") { execute(CommandId.DRAG_END) }
                TestButton("Drag Cancel") { execute(CommandId.DRAG_CANCEL) }
            }

            Text(
                text = "Last Result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = lastResult,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(400.dp))
            Text("Scrollable test content 1")
            Spacer(modifier = Modifier.height(400.dp))
            Text("Scrollable test content 2")
            Spacer(modifier = Modifier.height(400.dp))
            Text("Scrollable test content 3")
        }
    }
}

@Composable
private fun PointerPreview(pointer: PointerFrame) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .align(pointer.toAlignment())
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (pointer.faceDetected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
        )
    }
}

@Composable
private fun ButtonGrid(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TestButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(text)
    }
}

private fun PointerFrame.toAlignment(): Alignment {
    val horizontalBias = (x.coerceIn(0f, 1f) * 2f) - 1f
    val verticalBias = (y.coerceIn(0f, 1f) * 2f) - 1f
    return BiasAlignment(horizontalBias, verticalBias)
}

private fun ExecutionResult.toDisplayText(): String {
    return buildString {
        append("commandId=").append(commandId)
        append("\nsuccess=").append(success)
        append("\nexecutedAt=").append(executedAt?.let {
            "x=${it.x.format2()}, y=${it.y.format2()}, faceDetected=${it.faceDetected}"
        } ?: "null")
        append("\nerrorReason=").append(errorReason)
    }
}

private fun Float.format2(): String {
    return "%.2f".format(this)
}

@Preview(showBackground = true)
@Composable
fun MockInputTestPanelPreview() {
    Hands_free_controllerTheme {
        MockInputTestPanel()
    }
}
