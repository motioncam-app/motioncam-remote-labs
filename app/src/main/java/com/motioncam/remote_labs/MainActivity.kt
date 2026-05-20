package com.motioncam.remote_labs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motioncam.remote_labs.remote.CameraState
import com.motioncam.remote_labs.ui.theme.MotionCamRemoteLabsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotionCamRemoteLabsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel = viewModel<MainViewModel>()
                    val uiState by viewModel.uiState.collectAsState()
                    MotionCamRemoteApp(
                        uiState = uiState,
                        onUrlChange = viewModel::updateUrl,
                        onPairingCodeChange = viewModel::updatePairingCode,
                        onCertChange = viewModel::updateCertSha256,
                        onQrPayloadChange = viewModel::updateQrPayload,
                        onIsoChange = viewModel::updateIsoInput,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onRefresh = viewModel::refreshState,
                        onSetIso = viewModel::setIso,
                        onSetIsoAuto = viewModel::setIsoAuto,
                        onResetManual = viewModel::resetManual,
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionCamRemoteApp(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onCertChange: (String) -> Unit,
    onQrPayloadChange: (String) -> Unit,
    onIsoChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetIso: () -> Unit,
    onSetIsoAuto: () -> Unit,
    onResetManual: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MotionCam Remote",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            StatusCard(uiState)

            PairingCard(
                uiState = uiState,
                onUrlChange = onUrlChange,
                onPairingCodeChange = onPairingCodeChange,
                onCertChange = onCertChange,
                onQrPayloadChange = onQrPayloadChange,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )

            CameraStateCard(uiState.cameraState, onRefresh, uiState.isConnected, uiState.isBusy)

            CameraControlsCard(
                uiState = uiState,
                onIsoChange = onIsoChange,
                onSetIso = onSetIso,
                onSetIsoAuto = onSetIsoAuto,
                onResetManual = onResetManual,
            )
        }
    }
}

@Composable
private fun StatusCard(uiState: MainUiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            KeyValue("Connection", if (uiState.isBusy) "${uiState.status}..." else uiState.status)
            if (uiState.serverName.isNotBlank()) {
                KeyValue("Server", uiState.serverName)
            }
            if (uiState.capabilities.isNotEmpty()) {
                KeyValue("Capabilities", uiState.capabilities.joinToString())
            }
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PairingCard(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onCertChange: (String) -> Unit,
    onQrPayloadChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Pairing", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.qrPayload,
                onValueChange = onQrPayloadChange,
                label = { Text("QR payload") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy && !uiState.isConnected,
            )
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                label = { Text("WebSocket URL") },
                placeholder = { Text("wss://192.168.1.23:8765") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy && !uiState.isConnected && uiState.qrPayload.isBlank(),
            )
            OutlinedTextField(
                value = uiState.pairingCode,
                onValueChange = onPairingCodeChange,
                label = { Text("Pairing code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy && !uiState.isConnected && uiState.qrPayload.isBlank(),
            )
            OutlinedTextField(
                value = uiState.certSha256,
                onValueChange = onCertChange,
                label = { Text("Certificate SHA-256") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy && !uiState.isConnected && uiState.qrPayload.isBlank(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = !uiState.isBusy && !uiState.isConnected,
                ) {
                    Text("Connect")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = uiState.isConnected || uiState.isBusy,
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun CameraStateCard(
    cameraState: CameraState?,
    onRefresh: () -> Unit,
    isConnected: Boolean,
    isBusy: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Camera State", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onRefresh, enabled = isConnected && !isBusy) {
                    Text("Refresh")
                }
            }

            if (cameraState == null) {
                Text("No state yet")
                return@Column
            }

            KeyValue("Camera", cameraState.cameraId)
            KeyValue("Mode", cameraState.captureMode)
            KeyValue("Controller", cameraState.controllerState)
            KeyValue("Running", cameraState.cameraRunning.toString())
            KeyValue("Preview", cameraState.previewRunning.toString())
            Spacer(Modifier.height(4.dp))
            KeyValue("ISO", cameraState.iso?.toString() ?: "-")
            KeyValue("Shutter ns", cameraState.shutterNs?.toString() ?: "-")
            KeyValue("Focus", cameraState.focusDistance?.toString() ?: "-")
            KeyValue("Manual focus", cameraState.manualFocusEnabled?.toString() ?: "-")
            KeyValue(
                "White balance",
                listOfNotNull(
                    cameraState.whiteBalanceTemperature?.let { "${it}K" },
                    cameraState.whiteBalanceTint?.let { "tint $it" },
                ).joinToString().ifBlank { "-" },
            )
        }
    }
}

@Composable
private fun CameraControlsCard(
    uiState: MainUiState,
    onIsoChange: (String) -> Unit,
    onSetIso: () -> Unit,
    onSetIsoAuto: () -> Unit,
    onResetManual: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Camera Controls", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.isoInput,
                onValueChange = onIsoChange,
                label = { Text("ISO") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isConnected && !uiState.isBusy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSetIso,
                    enabled = uiState.isConnected && !uiState.isBusy,
                ) {
                    Text("Set ISO")
                }
                OutlinedButton(
                    onClick = onSetIsoAuto,
                    enabled = uiState.isConnected && !uiState.isBusy,
                ) {
                    Text("ISO Auto")
                }
            }
            OutlinedButton(
                onClick = onResetManual,
                enabled = uiState.isConnected && !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset Manual")
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun MotionCamRemoteAppPreview() {
    MotionCamRemoteLabsTheme {
        MotionCamRemoteApp(
            uiState = MainUiState(
                status = "Connected",
                isConnected = true,
                serverName = "MotionCam",
                capabilities = listOf("state.get", "camera.setIso"),
                cameraState = CameraState(
                    cameraId = "0",
                    captureMode = "VIDEO",
                    controllerState = "ACTIVE",
                    cameraRunning = true,
                    previewRunning = true,
                    iso = 400,
                    shutterNs = 10000000,
                    focusDistance = 2.0,
                    manualFocusEnabled = true,
                    whiteBalanceTemperature = 5600,
                    whiteBalanceTint = 0,
                ),
            ),
            onUrlChange = {},
            onPairingCodeChange = {},
            onCertChange = {},
            onQrPayloadChange = {},
            onIsoChange = {},
            onConnect = {},
            onDisconnect = {},
            onRefresh = {},
            onSetIso = {},
            onSetIsoAuto = {},
            onResetManual = {},
        )
    }
}

