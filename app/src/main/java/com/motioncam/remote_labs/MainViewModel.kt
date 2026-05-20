package com.motioncam.remote_labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motioncam.remote_labs.remote.CameraState
import com.motioncam.remote_labs.remote.MotionCamRemoteClient
import com.motioncam.remote_labs.remote.PairingDetails
import com.motioncam.remote_labs.remote.parsePairingDetails
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

            client?.close()
            client = MotionCamRemoteClient(pairing)

            _uiState.update {
                it.copy(
                    isBusy = true,
                    isConnected = false,
                    status = "Connecting",
                    error = null,
                    cameraState = null,
                )
            }

            runCatching {
                val auth = client!!.connectAndAuthenticate()
                val state = client!!.getState()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isConnected = true,
                        status = "Connected",
                        serverName = auth.serverName,
                        capabilities = auth.capabilities,
                        cameraState = state,
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
            )
        }
    }

    fun refreshState() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching { activeClient.getState() }
                .onSuccess { state -> _uiState.update { it.copy(isBusy = false, cameraState = state) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, error = error.toUserMessage()) } }
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
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                activeClient.setIso(iso)
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(isBusy = false, cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(isBusy = false, error = error.toUserMessage()) }
            }
        }
    }

    fun setIsoAuto() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                activeClient.setIsoAuto()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(isBusy = false, cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(isBusy = false, error = error.toUserMessage()) }
            }
        }
    }

    fun resetManual() {
        val activeClient = client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                activeClient.resetManual()
                activeClient.getState()
            }.onSuccess { state ->
                _uiState.update { it.copy(isBusy = false, cameraState = state) }
            }.onFailure { error ->
                _uiState.update { it.copy(isBusy = false, error = error.toUserMessage()) }
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

private fun Throwable.toUserMessage(): String {
    if (this is SSLHandshakeException && message?.contains("connection closed", ignoreCase = true) == true) {
        return "TLS handshake failed before pairing. MotionCam may be unable to serve WSS on this device; enable insecure lab transport in MotionCam Remote settings and scan the ws:// QR code."
    }
    return message ?: javaClass.simpleName
}
