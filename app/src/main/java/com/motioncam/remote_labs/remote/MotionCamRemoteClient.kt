package com.motioncam.remote_labs.remote

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext

class MotionCamRemoteClient(
    private val pairingDetails: PairingDetails,
    private val clientName: String = "MotionCam Remote Labs",
    private val onPreviewFrame: (PreviewFrame) -> Unit = {},
) : Closeable {
    private companion object {
        const val TAG = "MotionCamRemote"
    }

    private val requestIds = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val connected = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<Unit>()
    private val okHttpClient = buildOkHttpClient(pairingDetails)
    private var webSocket: WebSocket? = null

    suspend fun connectAndAuthenticate(): AuthResult = withContext(Dispatchers.IO) {
        if (pairingDetails.isExpired) {
            throw IllegalStateException("Pairing code has expired. Regenerate it in MotionCam.")
        }

        val request = Request.Builder()
            .url(pairingDetails.url)
            .build()
        Log.i(TAG, "Opening WebSocket to ${pairingDetails.url}")
        webSocket = okHttpClient.newWebSocket(request, Listener())

        withTimeout(10_000) { connected.await() }
        val id = nextId("auth")
        Log.i(TAG, "Sending auth.hello")
        val result = sendRequest(id, authRequest(id, clientName, pairingDetails))
        Log.i(TAG, "Authentication accepted")
        parseAuthResult(result)
    }

    suspend fun getState(): CameraState {
        val result = sendProtocolRequest("state.get")
        return CameraState.fromJson(result)
    }

    suspend fun listCameras(): List<CameraInfo> {
        val result = sendProtocolRequest("camera.list")
        return parseCameraList(result)
    }

    suspend fun listLenses(): List<LensInfo> {
        val result = sendProtocolRequest("lens.list")
        return parseLensList(result)
    }

    suspend fun listProfiles(): List<ProfileInfo> {
        val result = sendProtocolRequest("profile.list")
        return parseProfileList(result)
    }

    suspend fun selectProfile(profileId: String) {
        sendProtocolRequest("profile.select", JSONObject().put("profileId", profileId))
    }

    suspend fun setCaptureMode(mode: String) {
        sendProtocolRequest("capture.setMode", JSONObject().put("mode", mode))
    }

    suspend fun startCapture() {
        sendProtocolRequest("capture.start")
    }

    suspend fun stopCapture() {
        sendProtocolRequest("capture.stop")
    }

    suspend fun setIso(iso: Int) {
        sendProtocolRequest("camera.setIso", JSONObject().put("iso", iso))
    }

    suspend fun setIsoAuto() {
        sendProtocolRequest("camera.setIsoAuto")
    }

    suspend fun setShutterNs(shutterNs: Long) {
        sendProtocolRequest("camera.setShutterNs", JSONObject().put("shutterNs", shutterNs))
    }

    suspend fun setShutterAuto() {
        sendProtocolRequest("camera.setShutterAuto")
    }

    suspend fun setWhiteBalance(temperature: Int, tint: Int = 0) {
        sendProtocolRequest(
            "camera.setWhiteBalance",
            JSONObject()
                .put("temperature", temperature)
                .put("tint", tint)
        )
    }

    suspend fun setWhiteBalanceAuto() {
        sendProtocolRequest("camera.setWhiteBalanceAuto")
    }

    suspend fun startPreview() {
        sendProtocolRequest(
            "preview.start",
            JSONObject()
                .put("maxWidth", 960)
                .put("quality", 70)
                .put("fps", 5)
        )
    }

    suspend fun stopPreview() {
        sendProtocolRequest("preview.stop")
    }

    suspend fun resetManual() {
        sendProtocolRequest("camera.resetManual")
    }

    override fun close() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        webSocket?.close(1000, "Client closed")
        webSocket = null
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    private suspend fun sendProtocolRequest(method: String, params: JSONObject? = null): JSONObject {
        val id = nextId(method.substringAfterLast('.'))
        Log.d(TAG, "Sending $method with id $id")
        return sendRequest(id, request(id, method, params))
    }

    private suspend fun sendRequest(id: String, payload: String): JSONObject {
        val socket = webSocket ?: throw IllegalStateException("Not connected.")
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred

        if (!socket.send(payload)) {
            pending.remove(id)
            throw IllegalStateException("Unable to send request.")
        }

        try {
            return withTimeout(10_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw e
        }
    }

    private fun nextId(prefix: String): String = "$prefix-${requestIds.incrementAndGet()}"

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket opened with HTTP ${response.code}")
            connected.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket text: $text")
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            val id = message.optString("id", "")
            if (id.isBlank()) {
                val event = message.optString("event", "")
                if (event.isNotBlank()) {
                    Log.d(TAG, "WebSocket event: $event")
                }
                return
            }

            val deferred = pending.remove(id) ?: return
            val error = message.optJSONObject("error")
            if (error != null) {
                Log.w(TAG, "Request $id failed: $error")
                deferred.completeExceptionally(
                    MotionCamRemoteException(
                        MotionCamError(
                            code = error.optString("code", "UNKNOWN"),
                            message = error.optString("message", "Unknown MotionCam error."),
                        )
                    )
                )
                return
            }

            deferred.complete(message.optJSONObject("result") ?: JSONObject())
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val frame = parsePreviewFrame(bytes.toByteArray())
            if (frame != null) {
                onPreviewFrame(frame)
            } else {
                Log.w(TAG, "Ignoring unrecognized binary WebSocket frame: ${bytes.size} bytes")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure. HTTP ${response?.code}", t)
            connected.completeExceptionally(t)
            closed.complete(Unit)
            failPending(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closed: code=$code reason=$reason")
            closed.complete(Unit)
            failPending(IllegalStateException("Socket closed: $code $reason"))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket closing: code=$code reason=$reason")
        }
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }
}

class MotionCamRemoteException(
    val remoteError: MotionCamError,
) : RuntimeException("${remoteError.code}: ${remoteError.message}")

private fun buildOkHttpClient(pairingDetails: PairingDetails): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)

    val certSha256 = pairingDetails.certSha256
    if (pairingDetails.url.startsWith("wss://") && !certSha256.isNullOrBlank()) {
        val trustManager = FingerprintTrustManager(certSha256)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier { _, session ->
            runCatching {
                session.peerCertificates.firstOrNull()?.encoded?.sha256Fingerprint() ==
                    certSha256.normalizeFingerprint()
            }.getOrDefault(false)
        }
    }

    return builder.build()
}
