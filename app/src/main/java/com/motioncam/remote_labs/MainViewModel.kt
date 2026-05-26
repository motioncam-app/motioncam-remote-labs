package com.motioncam.remote_labs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motioncam.remote_labs.remote.CameraState
import com.motioncam.remote_labs.remote.LensInfo
import com.motioncam.remote_labs.remote.MotionCamRemoteClient
import com.motioncam.remote_labs.remote.PairingDetails
import com.motioncam.remote_labs.remote.ProfileInfo
import com.motioncam.remote_labs.remote.parsePairingDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.net.ssl.SSLHandshakeException

data class MainUiState(
    val url: String = "",
    val pairingCode: String = "",
    val certSha256: String = "",
    val qrPayload: String = "",
    val isBusy: Boolean = false,
    val isConnected: Boolean = false,
    val status: String = "Disconnected",
    val error: String? = null,
    val serverName: String = "",
    val capabilities: List<String> = emptyList(),
    val cameraState: CameraState? = null,
    val lenses: List<LensInfo> = emptyList(),
    val profiles: List<ProfileInfo> = emptyList(),
    val previewFrame: Bitmap? = null,
    val previewStatus: String = "Preview stopped",
    val isoInput: String = "400",
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var client: MotionCamRemoteClient? = null

    fun updateUrl(value: String) = _uiState.update { it.copy(url = value, error = null) }

    fun updatePairingCode(value: String) = _uiState.update { it.copy(pairingCode = value, error = null) }

    fun updateCertSha256(value: String) = _uiState.update { it.copy(certSha256 = value, error = null) }

    fun updateQrPayload(value: String) = _uiState.update { it.copy(qrPayload = value, error = null) }

    fun updateIsoInput(value: String) = _uiState.update { it.copy(isoInput = value.filter { char -> char.isDigit() }) }

    fun connect() {
        viewModelScope.launch {
            val pairing = buildPairingDetails()
            if (pairing == null) {
                return@launch
            }

            connect(pairing)
        }
    }

    fun connectWithQrPayload(payload: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(qrPayload = payload, error = null) }
            val pairing = parsePairingDetails(payload.trim())
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
                .getOrNull() ?: return@launch

            connect(pairing)
        }
    }

    private suspend fun connect(pairing: PairingDetails) {
        client?.close()
        client = MotionCamRemoteClient(pairing) { frame ->
            val bitmap = BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
            if (bitmap != null) {
                _uiState.update {
                    it.copy(
                        previewFrame = bitmap,
                        previewStatus = "${frame.width}x${frame.height} #${frame.sequence}",
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                isBusy = true,
                isConnected = false,
                status = "Connecting",
                error = null,
                cameraState = null,
                previewFrame = null,
                previewStatus = "Preview stopped",
            )
        }

        runCatching {
            val auth = client!!.connectAndAuthenticate()
            val state = client!!.getState()
            val lenses = runCatching {
                if ("lens.list" in auth.capabilities) client!!.listLenses() else emptyList()
            }.getOrDefault(emptyList())
            val profiles = runCatching {
                if ("profile.list" in auth.capabilities) client!!.listProfiles() else emptyList()
            }.getOrDefault(emptyList())
            val previewStatus = runCatching {
                client!!.startPreview()
                "Starting preview"
            }.getOrElse { error ->
                "Preview unavailable: ${error.toUserMessage()}"
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    isConnected = true,
                    status = "Connected",
                    serverName = auth.serverName,
                    capabilities = auth.capabilities,
                    cameraState = state,
                    lenses = lenses,
                    profiles = profiles,
                    previewStatus = previewStatus,
                )
            }
        }.onFailure { error ->
            client?.close()
            client = null
            _uiState.update {
                it.copy(
                    isBusy = false,
                    isConnected = false,
                    status = "Disconnected",
                    error = error.toUserMessage(),
                )
            }
        }
    }

    fun disconnect() {
        client?.close()
        client = null
        _uiState.update {
            it.copy(
                isBusy = false,
                isConnected = false,
                status = "Disconnected",
                serverName = "",
                capabilities = emptyList(),
                cameraState = null,
                lenses = emptyList(),
                profiles = emptyList(),
                previewFrame = null,
                previewStatus = "Preview stopped",
            )
        }
    }

    fun refreshState() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching { activeClient.getState() }
                .onSuccess { state -> _uiState.update { it.copy(cameraState = state) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.toUserMessage()) } }
        }
    }

    fun setCaptureMode(mode: String) {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setCaptureMode(mode)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun toggleCapture() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                if (uiState.value.cameraState?.recordingActive == true) {
                    activeClient.stopCapture()
                } else {
                    activeClient.startCapture()
                }
                activeClient.getSettledCaptureState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setIso() {
        val activeClient = client ?: return
        val iso = uiState.value.isoInput.toIntOrNull()
        if (iso == null) {
            _uiState.update { it.copy(error = "Enter a valid ISO value.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setIso(iso)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setIsoAuto() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setIsoAuto()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setIsoValue(iso: Int) {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isoInput = iso.toString()) }
            runCatching {
                activeClient.setIso(iso)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setShutterNs(shutterNs: Long) {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setShutterNs(shutterNs)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setShutterAuto() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setShutterAuto()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setWhiteBalance(temperature: Int, tint: Int = 0) {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setWhiteBalance(temperature, tint)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun setWhiteBalanceAuto() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.setWhiteBalanceAuto()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    fun resetManual() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            runCatching {
                activeClient.resetManual()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.toUserMessage()) }
            }
        }
    }

    override fun onCleared() {
        client?.close()
        super.onCleared()
    }

    private fun buildPairingDetails(): PairingDetails? {
        val state = uiState.value
        val payload = state.qrPayload.trim()
        if (payload.isNotBlank()) {
            return parsePairingDetails(payload)
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
                .getOrNull()
        }

        if (state.url.isBlank() || state.pairingCode.isBlank()) {
            _uiState.update { it.copy(error = "Enter the MotionCam URL and pairing code.") }
            return null
        }

        return PairingDetails(
            url = state.url.trim(),
            pairingCode = state.pairingCode.trim(),
            certSha256 = state.certSha256.trim().ifBlank { null },
        )
    }
}

private suspend fun MotionCamRemoteClient.getSettledCaptureState(): CameraState {
    var state = getState()
    repeat(30) {
        if (!state.recordingFinalizing) {
            return state
        }
        delay(500)
        state = getState()
    }
    return state
}

private fun Throwable.toUserMessage(): String {
    if (this is SSLHandshakeException && message?.contains("connection closed", ignoreCase = true) == true) {
        return "TLS handshake failed before pairing. MotionCam may be unable to serve WSS on this device; enable insecure lab transport in MotionCam Remote settings and scan the ws:// QR code."
    }
    return message ?: javaClass.simpleName
}
