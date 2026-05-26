package com.motioncam.remote_labs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motioncam.remote_labs.remote.CameraState
import com.motioncam.remote_labs.ui.QrScannerView
import com.motioncam.remote_labs.ui.theme.MotionCamRemoteLabsTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PairingMode {
    Start,
    Manual,
}

private data class ShutterPreset(
    val label: String,
    val shutterNs: Long,
)

private data class CaptureModeOption(
    val mode: String,
    val label: String,
)

private val ShutterPresets = listOf(
    ShutterPreset("1/24", 41_666_667L),
    ShutterPreset("1/48", 20_833_333L),
    ShutterPreset("1/60", 16_666_667L),
    ShutterPreset("1/120", 8_333_333L),
)

private val IsoPresets = listOf(100, 200, 400, 800, 1600)

private val WhiteBalancePresets = listOf(
    "3200K" to 3200,
    "4300K" to 4300,
    "5600K" to 5600,
    "6500K" to 6500,
)

private val CaptureModeOptions = listOf(
    CaptureModeOption("ZSL", "Photo"),
    CaptureModeOption("BURST", "Burst"),
    CaptureModeOption("RAW_VIDEO", "Raw Video"),
    CaptureModeOption("LOG_VIDEO", "DirectLog"),
    CaptureModeOption("TIMELAPSE", "Timelapse"),
)

private enum class ControlBank {
    Iso,
    Shutter,
    WhiteBalance,
}

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
                        onConnectQrPayload = viewModel::connectWithQrPayload,
                        onDisconnect = viewModel::disconnect,
                        onRefresh = viewModel::refreshState,
                        onSetIso = viewModel::setIso,
                        onSetIsoValue = viewModel::setIsoValue,
                        onSetIsoAuto = viewModel::setIsoAuto,
                        onSetShutterNs = viewModel::setShutterNs,
                        onSetShutterAuto = viewModel::setShutterAuto,
                        onSetWhiteBalance = viewModel::setWhiteBalance,
                        onSetWhiteBalanceAuto = viewModel::setWhiteBalanceAuto,
                        onResetManual = viewModel::resetManual,
                        onSetCaptureMode = viewModel::setCaptureMode,
                        onToggleCapture = viewModel::toggleCapture,
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
    onConnectQrPayload: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSetIso: () -> Unit,
    onSetIsoValue: (Int) -> Unit,
    onSetIsoAuto: () -> Unit,
    onSetShutterNs: (Long) -> Unit,
    onSetShutterAuto: () -> Unit,
    onSetWhiteBalance: (Int) -> Unit,
    onSetWhiteBalanceAuto: () -> Unit,
    onResetManual: () -> Unit,
    onSetCaptureMode: (String) -> Unit,
    onToggleCapture: () -> Unit,
) {
    val context = LocalContext.current
    var pairingMode by rememberSaveable { mutableStateOf(PairingMode.Start) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var scannerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scannerMessage = null
            showScanner = true
        } else {
            scannerMessage = "Camera permission is required to scan the pairing QR code."
        }
    }

    fun openScanner() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            scannerMessage = null
            showScanner = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showScanner) {
        QrScannerView(
            onQrCodeScanned = { text ->
                onQrPayloadChange(text)
                scannerMessage = null
                showScanner = false
                pairingMode = PairingMode.Start
                onConnectQrPayload(text)
            },
            onCancel = { showScanner = false },
        )
        return
    }

    if (uiState.isConnected) {
        CameraRemoteScreen(
            uiState = uiState,
            onDisconnect = {
                onDisconnect()
                pairingMode = PairingMode.Start
            },
            onRefresh = onRefresh,
            onIsoChange = onIsoChange,
            onSetIso = onSetIso,
            onSetIsoValue = onSetIsoValue,
            onSetIsoAuto = onSetIsoAuto,
            onSetShutterNs = onSetShutterNs,
            onSetShutterAuto = onSetShutterAuto,
            onSetWhiteBalance = onSetWhiteBalance,
            onSetWhiteBalanceAuto = onSetWhiteBalanceAuto,
            onResetManual = onResetManual,
            onSetCaptureMode = onSetCaptureMode,
            onToggleCapture = onToggleCapture,
        )
        return
    }

    when (pairingMode) {
        PairingMode.Start -> StartScreen(
            uiState = uiState,
            scannerMessage = scannerMessage,
            onScanQr = ::openScanner,
            onManual = { pairingMode = PairingMode.Manual },
        )

        PairingMode.Manual -> ManualPairingScreen(
            uiState = uiState,
            onBack = { pairingMode = PairingMode.Start },
            onUrlChange = onUrlChange,
            onPairingCodeChange = onPairingCodeChange,
            onCertChange = onCertChange,
            onQrPayloadChange = onQrPayloadChange,
            onScanQr = ::openScanner,
            onConnect = onConnect,
        )
    }
}

@Composable
private fun StartScreen(
    uiState: MainUiState,
    scannerMessage: String?,
    onScanQr: () -> Unit,
    onManual: () -> Unit,
) {
    Scaffold(containerColor = Color.Black) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "MotionCam Remote",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isBusy) Color(0xFFE6A23C) else Color(0xFF353B45))
                )
                Text(
                    text = if (uiState.isBusy) "Connecting" else "Disconnected",
                    color = Color(0xFF9AA3AF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(42.dp))
            Button(
                onClick = onScanQr,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE6A23C),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF4A3A1D),
                    disabledContentColor = Color(0xFF8E97A5),
                ),
            ) {
                Text(
                    text = if (uiState.isBusy) "Connecting" else "Scan Pairing QR",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onManual,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, Color(0xFF3A414C)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFD6DAE1),
                ),
            ) {
                Text("Enter Manually")
            }
            StatusText(uiState.error ?: scannerMessage)
        }
    }
}

@Composable
private fun ManualPairingScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onCertChange: (String) -> Unit,
    onQrPayloadChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onConnect: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pair MotionCam", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onBack, enabled = !uiState.isBusy) {
                    Text("Back")
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onScanQr,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Scan QR")
                    }
                    OutlinedTextField(
                        value = uiState.qrPayload,
                        onValueChange = onQrPayloadChange,
                        label = { Text("QR payload") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isBusy,
                    )
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = onUrlChange,
                        label = { Text("WebSocket URL") },
                        placeholder = { Text("wss://192.168.1.23:8765") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isBusy && uiState.qrPayload.isBlank(),
                    )
                    OutlinedTextField(
                        value = uiState.pairingCode,
                        onValueChange = onPairingCodeChange,
                        label = { Text("Pairing code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isBusy && uiState.qrPayload.isBlank(),
                    )
                    OutlinedTextField(
                        value = uiState.certSha256,
                        onValueChange = onCertChange,
                        label = { Text("Certificate SHA-256") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isBusy && uiState.qrPayload.isBlank(),
                    )
                    Button(
                        onClick = onConnect,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isBusy) "Connecting..." else "Connect")
                    }
                    uiState.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRemoteScreen(
    uiState: MainUiState,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onIsoChange: (String) -> Unit,
    onSetIso: () -> Unit,
    onSetIsoValue: (Int) -> Unit,
    onSetIsoAuto: () -> Unit,
    onSetShutterNs: (Long) -> Unit,
    onSetShutterAuto: () -> Unit,
    onSetWhiteBalance: (Int) -> Unit,
    onSetWhiteBalanceAuto: () -> Unit,
    onResetManual: () -> Unit,
    onSetCaptureMode: (String) -> Unit,
    onToggleCapture: () -> Unit,
) {
    Scaffold(containerColor = Color.Black) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .background(Color.Black)
                .fillMaxSize()
        ) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                PreviewPanel(
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize(),
                    showHeader = false,
                    onSetCaptureMode = onSetCaptureMode,
                    onToggleCapture = onToggleCapture,
                    captureBottomPadding = 122.dp,
                    compactCaptureControls = true,
                )
                CameraTopBar(
                    uiState = uiState,
                    onDisconnect = onDisconnect,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    compact = true,
                )
                LandscapeControlStrip(
                    uiState = uiState,
                    onSetIsoValue = onSetIsoValue,
                    onSetIsoAuto = onSetIsoAuto,
                    onSetShutterNs = onSetShutterNs,
                    onSetShutterAuto = onSetShutterAuto,
                    onSetWhiteBalance = onSetWhiteBalance,
                    onSetWhiteBalanceAuto = onSetWhiteBalanceAuto,
                    onRefresh = onRefresh,
                    onResetManual = onResetManual,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    CameraTopBar(
                        uiState = uiState,
                        onDisconnect = onDisconnect,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    PreviewPanel(
                        uiState = uiState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onSetCaptureMode = onSetCaptureMode,
                        onToggleCapture = onToggleCapture,
                    )
                    CameraControlDeck(
                        uiState = uiState,
                        onSetIsoValue = onSetIsoValue,
                        onSetIsoAuto = onSetIsoAuto,
                        onSetShutterNs = onSetShutterNs,
                        onSetShutterAuto = onSetShutterAuto,
                        onSetWhiteBalance = onSetWhiteBalance,
                        onSetWhiteBalanceAuto = onSetWhiteBalanceAuto,
                        onRefresh = onRefresh,
                        onResetManual = onResetManual,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        landscape = false,
                    )
                }
            }

            uiState.error?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFB4AB),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.72f), MaterialTheme.shapes.small)
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    uiState: MainUiState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = uiState.serverName.ifBlank { "MotionCam" },
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (uiState.isBusy) Color(0xFFFFB300) else Color(0xFF2E7D32))
                        .padding(5.dp)
                )
                Text(
                    if (uiState.isBusy) "Working" else "Connected",
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB9C0CC),
                )
            }
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.height(if (compact) 32.dp else 40.dp),
            border = BorderStroke(1.dp, Color(0xFF3A414C)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD6DAE1)),
        ) {
            Text("Disconnect", style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PreviewPanel(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onSetCaptureMode: (String) -> Unit = {},
    onToggleCapture: () -> Unit = {},
    captureBottomPadding: androidx.compose.ui.unit.Dp = 18.dp,
    compactCaptureControls: Boolean = false,
) {
    val cameraState = uiState.cameraState
    Box(
        modifier = modifier
            .background(Color(0xFF090A0D)),
    ) {
        val preview = uiState.previewFrame
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101216)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.previewStatus,
                    color = Color(0xFF9AA3AF),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (showHeader) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cameraState?.captureMode ?: "REMOTE",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.previewStatus,
                    color = Color(0xFFD6DAE1),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        CaptureControlsOverlay(
            uiState = uiState,
            onSetCaptureMode = onSetCaptureMode,
            onToggleCapture = onToggleCapture,
            compact = compactCaptureControls,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = captureBottomPadding),
        )

    }
}

@Composable
private fun CaptureControlsOverlay(
    uiState: MainUiState,
    onSetCaptureMode: (String) -> Unit,
    onToggleCapture: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
    val cameraState = uiState.cameraState
    val captureMode = cameraState?.captureMode ?: "-"
    val captureModeLabel = CaptureModeOptions.firstOrNull { it.mode == captureMode }?.label ?: captureMode
    val recordingActive = cameraState?.recordingActive == true
    val recordingFinalizing = cameraState?.recordingFinalizing == true
    val canSetMode = "capture.setMode" in uiState.capabilities
    val canStart = "capture.start" in uiState.capabilities
    val canStop = "capture.stop" in uiState.capabilities
    val canToggle = !uiState.isBusy && !recordingFinalizing && if (recordingActive) canStop else canStart
    val canChangeMode = !uiState.isBusy && !recordingActive && !recordingFinalizing && canSetMode
    val isVideoMode = captureMode in setOf("RAW_VIDEO", "LOG_VIDEO", "TIMELAPSE", "VIDEO")
    val captureLabel = when {
        recordingFinalizing -> "WAIT"
        recordingActive -> "STOP"
        isVideoMode -> "REC"
        else -> "SHOT"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            ModeSelectorButton(
                label = captureModeLabel,
                enabled = canChangeMode,
                menuOpen = modeMenuOpen,
                onClick = { modeMenuOpen = true },
                compact = compact,
            )
            if (modeMenuOpen) {
                Popup(
                    alignment = Alignment.BottomCenter,
                    onDismissRequest = { modeMenuOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    CaptureModePopup(
                        currentMode = captureMode,
                        enabled = canChangeMode,
                        onSelect = { mode ->
                            modeMenuOpen = false
                            onSetCaptureMode(mode)
                        },
                        modifier = Modifier.padding(bottom = if (compact) 42.dp else 48.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(if (compact) 66.dp else 76.dp)
                .alpha(if (canToggle) 1f else 0.45f)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = canToggle, onClick = onToggleCapture)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (recordingActive) Color(0xFFB42318) else Color(0xFF7A1712)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = captureLabel,
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ModeSelectorButton(
    label: String,
    enabled: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val borderColor = if (menuOpen) Color(0xFFE6A23C) else Color(0xFF4A515C)
    Box(
        modifier = Modifier
            .height(if (compact) 34.dp else 38.dp)
            .width(if (compact) 118.dp else 138.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CaptureModePopup(
    currentMode: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(176.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(0xEE071017))
            .border(1.dp, Color(0xFF2D3945), MaterialTheme.shapes.small)
            .padding(vertical = 6.dp),
    ) {
        CaptureModeOptions.forEach { option ->
            val selected = currentMode == option.mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(if (selected) Color(0xFFE6A23C).copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = enabled && !selected) { onSelect(option.mode) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    color = if (selected) Color(0xFFE6A23C) else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (selected) {
                    Text(
                        text = "ACTIVE",
                        color = Color(0xFFE6A23C),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraControlDeck(
    uiState: MainUiState,
    onSetIsoValue: (Int) -> Unit,
    onSetIsoAuto: () -> Unit,
    onSetShutterNs: (Long) -> Unit,
    onSetShutterAuto: () -> Unit,
    onSetWhiteBalance: (Int) -> Unit,
    onSetWhiteBalanceAuto: () -> Unit,
    onRefresh: () -> Unit,
    onResetManual: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean,
) {
    var selectedBank by rememberSaveable { mutableStateOf(ControlBank.Iso) }
    val cameraState = uiState.cameraState
    val selectedIso = cameraState?.iso
    val selectedShutterNs = cameraState?.shutterNs
    val selectedWhiteBalance = cameraState?.whiteBalanceTemperature

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlReadout(
                label = "ISO",
                value = cameraState?.iso?.toString() ?: "-",
                selected = selectedBank == ControlBank.Iso,
                onClick = { selectedBank = ControlBank.Iso },
                modifier = Modifier.weight(1f),
            )
            ControlReadout(
                label = "SHUTTER",
                value = cameraState?.shutterNs?.let(::formatShutter) ?: "-",
                selected = selectedBank == ControlBank.Shutter,
                onClick = { selectedBank = ControlBank.Shutter },
                modifier = Modifier.weight(1f),
            )
            ControlReadout(
                label = "WB",
                value = cameraState?.whiteBalanceTemperature?.let { "${it}K" } ?: "-",
                selected = selectedBank == ControlBank.WhiteBalance,
                onClick = { selectedBank = ControlBank.WhiteBalance },
                modifier = Modifier.weight(1f),
            )
        }

        when (selectedBank) {
            ControlBank.Iso -> PresetRow {
                IsoPresets.forEach { iso ->
                    ControlTile(
                        label = iso.toString(),
                        onClick = { onSetIsoValue(iso) },
                        enabled = !uiState.isBusy,
                        selected = selectedIso == iso,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetIsoAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }

            ControlBank.Shutter -> PresetRow {
                ShutterPresets.forEach { preset ->
                    ControlTile(
                        label = preset.label,
                        onClick = { onSetShutterNs(preset.shutterNs) },
                        enabled = !uiState.isBusy,
                        selected = selectedShutterNs?.isCloseTo(preset.shutterNs, toleranceNs = 2_000_000L) == true,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetShutterAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }

            ControlBank.WhiteBalance -> PresetRow {
                WhiteBalancePresets.forEach { (label, temperature) ->
                    ControlTile(
                        label = label,
                        onClick = { onSetWhiteBalance(temperature) },
                        enabled = !uiState.isBusy,
                        selected = selectedWhiteBalance == temperature,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetWhiteBalanceAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CameraActionButton(
                label = "Refresh",
                onClick = onRefresh,
                enabled = !uiState.isBusy,
                modifier = Modifier.weight(1f),
            )
            CameraActionButton(
                label = "Reset Manual",
                onClick = onResetManual,
                enabled = !uiState.isBusy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LandscapeControlStrip(
    uiState: MainUiState,
    onSetIsoValue: (Int) -> Unit,
    onSetIsoAuto: () -> Unit,
    onSetShutterNs: (Long) -> Unit,
    onSetShutterAuto: () -> Unit,
    onSetWhiteBalance: (Int) -> Unit,
    onSetWhiteBalanceAuto: () -> Unit,
    onRefresh: () -> Unit,
    onResetManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBank by rememberSaveable { mutableStateOf(ControlBank.Iso) }
    val cameraState = uiState.cameraState
    val selectedIso = cameraState?.iso
    val selectedShutterNs = cameraState?.shutterNs
    val selectedWhiteBalance = cameraState?.whiteBalanceTemperature

    BoxWithConstraints(modifier = modifier) {
        val narrow = maxWidth < 700.dp
        val actionWidth = if (narrow) 62.dp else 78.dp

        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlReadout(
                    label = "ISO",
                    value = selectedIso?.toString() ?: "-",
                    selected = selectedBank == ControlBank.Iso,
                    onClick = { selectedBank = ControlBank.Iso },
                    modifier = Modifier.weight(0.9f),
                    compact = true,
                )
                ControlReadout(
                    label = "SHUTTER",
                    value = selectedShutterNs?.let(::formatShutter) ?: "-",
                    selected = selectedBank == ControlBank.Shutter,
                    onClick = { selectedBank = ControlBank.Shutter },
                    modifier = Modifier.weight(1.1f),
                    compact = true,
                )
                ControlReadout(
                    label = "WB",
                    value = selectedWhiteBalance?.let { "${it}K" } ?: "-",
                    selected = selectedBank == ControlBank.WhiteBalance,
                    onClick = { selectedBank = ControlBank.WhiteBalance },
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                CameraActionButton(
                    label = "Sync",
                    onClick = onRefresh,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.width(actionWidth),
                    compact = true,
                )
                CameraActionButton(
                    label = "Reset",
                    onClick = onResetManual,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.width(actionWidth),
                    compact = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (selectedBank) {
                    ControlBank.Iso -> {
                        IsoPresets.forEach { iso ->
                            ControlTile(
                                label = iso.toString(),
                                onClick = { onSetIsoValue(iso) },
                                enabled = !uiState.isBusy,
                                selected = selectedIso == iso,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                        ControlTile(
                            label = "A",
                            onClick = onSetIsoAuto,
                            enabled = !uiState.isBusy,
                            modifier = Modifier.weight(1f),
                            accent = true,
                            compact = true,
                        )
                    }

                    ControlBank.Shutter -> {
                        ShutterPresets.forEach { preset ->
                            ControlTile(
                                label = preset.label,
                                onClick = { onSetShutterNs(preset.shutterNs) },
                                enabled = !uiState.isBusy,
                                selected = selectedShutterNs?.isCloseTo(preset.shutterNs, toleranceNs = 2_000_000L) == true,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                        ControlTile(
                            label = "A",
                            onClick = onSetShutterAuto,
                            enabled = !uiState.isBusy,
                            modifier = Modifier.weight(1f),
                            accent = true,
                            compact = true,
                        )
                    }

                    ControlBank.WhiteBalance -> {
                        WhiteBalancePresets.forEach { (label, temperature) ->
                            ControlTile(
                                label = label,
                                onClick = { onSetWhiteBalance(temperature) },
                                enabled = !uiState.isBusy,
                                selected = selectedWhiteBalance == temperature,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                        ControlTile(
                            label = "A",
                            onClick = onSetWhiteBalanceAuto,
                            enabled = !uiState.isBusy,
                            modifier = Modifier.weight(1f),
                            accent = true,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun ControlReadout(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val borderColor = if (selected) Color(0xFFE6A23C) else Color(0xFF3A414C)
    Column(
        modifier = modifier
            .height(if (compact) 48.dp else 58.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Color(0xFF9AA3AF), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            value,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CameraActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(if (compact) 42.dp else 38.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, Color(0xFF3A414C), MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color(0xFFD6DAE1),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun ExposureControls(
    uiState: MainUiState,
    onIsoChange: (String) -> Unit,
    onSetIso: () -> Unit,
    onSetIsoValue: (Int) -> Unit,
    onSetIsoAuto: () -> Unit,
    onSetShutterNs: (Long) -> Unit,
    onSetShutterAuto: () -> Unit,
    onSetWhiteBalance: (Int) -> Unit,
    onSetWhiteBalanceAuto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Camera Controls",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C313A), MaterialTheme.shapes.small)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ISO", color = Color(0xFFB9C0CC), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                IsoPresets.take(3).forEach { iso ->
                    ControlTile(
                        label = iso.toString(),
                        onClick = { onSetIsoValue(iso) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                IsoPresets.drop(3).forEach { iso ->
                    ControlTile(
                        label = iso.toString(),
                        onClick = { onSetIsoValue(iso) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetIsoAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C313A), MaterialTheme.shapes.small)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Shutter", color = Color(0xFFB9C0CC), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ShutterPresets.take(3).forEach { preset ->
                    ControlTile(
                        label = preset.label,
                        onClick = { onSetShutterNs(preset.shutterNs) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ShutterPresets.drop(3).forEach { preset ->
                    ControlTile(
                        label = preset.label,
                        onClick = { onSetShutterNs(preset.shutterNs) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetShutterAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C313A), MaterialTheme.shapes.small)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("White Balance", color = Color(0xFFB9C0CC), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WhiteBalancePresets.take(3).forEach { (label, temperature) ->
                    ControlTile(
                        label = label,
                        onClick = { onSetWhiteBalance(temperature) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WhiteBalancePresets.drop(3).forEach { (label, temperature) ->
                    ControlTile(
                        label = label,
                        onClick = { onSetWhiteBalance(temperature) },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                ControlTile(
                    label = "AUTO",
                    onClick = onSetWhiteBalanceAuto,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
            }
        }
    }
}

@Composable
private fun StatePanel(
    cameraState: CameraState?,
    onRefresh: () -> Unit,
    uiState: MainUiState,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("State", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onRefresh, enabled = !uiState.isBusy) {
                    Text("Refresh")
                }
            }

            if (cameraState == null) {
                Text("No state")
                return@Column
            }

            KeyValue("Camera", cameraState.cameraId)
            KeyValue("Controller", cameraState.controllerState)
            KeyValue("Running", cameraState.cameraRunning.toString())
            KeyValue("Preview", cameraState.previewRunning.toString())
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
private fun HudValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.22f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color(0xFFAEB6C2), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ControlTile(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val borderColor = when {
        selected -> Color(0xFFE6A23C)
        accent -> Color(0xFFE6A23C)
        else -> Color(0xFF424955)
    }
    val backgroundColor = if (selected) Color(0xFFE6A23C).copy(alpha = 0.18f) else Color.Transparent
    Box(
        modifier = modifier
            .height(if (compact) 40.dp else 38.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (accent) borderColor else Color(0xFFD6DAE1),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusText(text: String?) {
    if (text.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
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

private fun formatShutter(shutterNs: Long): String {
    if (shutterNs <= 0L) return "-"
    val denominator = (1_000_000_000.0 / shutterNs).roundToInt()
    return if (denominator > 1) "1/$denominator" else "${shutterNs / 1_000_000} ms"
}

private fun Long.isCloseTo(other: Long, toleranceNs: Long): Boolean =
    abs(this - other) <= toleranceNs

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    MotionCamRemoteLabsTheme {
        StartScreen(
            uiState = MainUiState(),
            scannerMessage = null,
            onScanQr = {},
            onManual = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraRemoteScreenPreview() {
    MotionCamRemoteLabsTheme {
        CameraRemoteScreen(
            uiState = MainUiState(
                status = "Connected",
                isConnected = true,
                serverName = "MotionCam",
                capabilities = listOf(
                    "state.get",
                    "camera.setIso",
                    "capture.setMode",
                    "capture.start",
                    "capture.stop",
                ),
                cameraState = CameraState(
                    cameraId = "0",
                    captureMode = "LOG_VIDEO",
                    controllerState = "ACTIVE",
                    cameraRunning = true,
                    previewRunning = true,
                    recordingActive = false,
                    recordingState = "STOPPED",
                    iso = 400,
                    shutterNs = 10_000_000,
                    focusDistance = 2.0,
                    manualFocusEnabled = true,
                    whiteBalanceTemperature = 5600,
                    whiteBalanceTint = 0,
                ),
            ),
            onDisconnect = {},
            onRefresh = {},
            onIsoChange = {},
            onSetIso = {},
            onSetIsoValue = {},
            onSetIsoAuto = {},
            onSetShutterNs = {},
            onSetShutterAuto = {},
            onSetWhiteBalance = {},
            onSetWhiteBalanceAuto = {},
            onResetManual = {},
            onSetCaptureMode = {},
            onToggleCapture = {},
        )
    }
}
