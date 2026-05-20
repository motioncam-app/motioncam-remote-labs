package com.motioncam.remote_labs.remote

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
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext

class MotionCamRemoteClient(
    private val pairingDetails: PairingDetails,
    private val clientName: String = "MotionCam Remote Labs",
) : Closeable {
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
        webSocket = okHttpClient.newWebSocket(request, Listener())

        withTimeout(10_000) { connected.await() }
        val id = nextId("auth")
        val result = sendRequest(id, authRequest(id, clientName, pairingDetails.pairingCode))
        parseAuthResult(result)
    }

    suspend fun getState(): CameraState {
        val result = sendProtocolRequest("state.get")
        return CameraState.fromJson(result)
    }

    suspend fun setIso(iso: Int) {
        sendProtocolRequest("camera.setIso", JSONObject().put("iso", iso))
    }

    suspend fun setIsoAuto() {
        sendProtocolRequest("camera.setIsoAuto")
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
            connected.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            val id = message.optString("id", "")
            if (id.isBlank()) {
                return
            }

            val deferred = pending.remove(id) ?: return
            val error = message.optJSONObject("error")
            if (error != null) {
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

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected.completeExceptionally(t)
            closed.complete(Unit)
            failPending(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Unit)
            failPending(IllegalStateException("Socket closed: $code $reason"))
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

