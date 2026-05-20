package com.motioncam.remote_labs.remote

import org.json.JSONArray
import org.json.JSONObject

data class MotionCamError(
    val code: String,
    val message: String,
)

data class AuthResult(
    val protocolVersion: Int,
    val authMode: String,
    val serverName: String,
    val clientName: String,
    val capabilities: List<String>,
)

data class CameraState(
    val cameraId: String = "-",
    val captureMode: String = "-",
    val controllerState: String = "-",
    val cameraRunning: Boolean = false,
    val previewRunning: Boolean = false,
    val iso: Int? = null,
    val shutterNs: Long? = null,
    val manualFocusEnabled: Boolean? = null,
    val focusDistance: Double? = null,
    val whiteBalanceTemperature: Int? = null,
    val whiteBalanceTint: Int? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): CameraState {
            val camera = json.optJSONObject("camera")
            val whiteBalance = camera?.optJSONObject("whiteBalance")
            return CameraState(
                cameraId = json.optString("cameraId", "-"),
                captureMode = json.optString("captureMode", "-"),
                controllerState = json.optString("controllerState", "-"),
                cameraRunning = json.optBoolean("cameraRunning", false),
                previewRunning = json.optBoolean("previewRunning", false),
                iso = camera?.optNullableInt("iso"),
                shutterNs = camera?.optNullableLong("shutterNs"),
                manualFocusEnabled = camera?.optNullableBoolean("manualFocusEnabled"),
                focusDistance = camera?.optNullableDouble("focusDistance"),
                whiteBalanceTemperature = whiteBalance?.optNullableInt("temperature"),
                whiteBalanceTint = whiteBalance?.optNullableInt("tint"),
            )
        }
    }
}

data class PreviewFrame(
    val jpegBytes: ByteArray,
    val streamId: String,
    val sequence: Long,
    val width: Int,
    val height: Int,
    val timestampMs: Long,
)

fun parsePreviewFrame(bytes: ByteArray): PreviewFrame? {
    if (bytes.size < 8) return null
    if (
        bytes[0] != 'M'.code.toByte() ||
        bytes[1] != 'C'.code.toByte() ||
        bytes[2] != 'P'.code.toByte() ||
        bytes[3] != 'V'.code.toByte()
    ) {
        return null
    }
    if (bytes[4].toInt() != 1 || bytes[5].toInt() != 1) {
        return null
    }

    val headerLength = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
    val headerStart = 8
    val jpegStart = headerStart + headerLength
    if (jpegStart >= bytes.size) return null

    val headerJson = bytes.decodeToString(headerStart, jpegStart)
    val header = JSONObject(headerJson)
    return PreviewFrame(
        jpegBytes = bytes.copyOfRange(jpegStart, bytes.size),
        streamId = header.optString("streamId", ""),
        sequence = header.optLong("sequence", 0L),
        width = header.optInt("width", 0),
        height = header.optInt("height", 0),
        timestampMs = header.optLong("timestampMs", 0L),
    )
}

internal fun authRequest(id: String, clientName: String, pairingCode: String): String {
    return JSONObject()
        .put("id", id)
        .put("method", "auth.hello")
        .put(
            "params",
            JSONObject()
                .put("clientName", clientName)
                .put("protocolVersion", 1)
                .put("pairingCode", pairingCode)
        )
        .toString()
}

internal fun request(id: String, method: String, params: JSONObject? = null): String {
    val json = JSONObject()
        .put("id", id)
        .put("method", method)
    if (params != null) {
        json.put("params", params)
    }
    return json.toString()
}

internal fun parseAuthResult(json: JSONObject): AuthResult {
    return AuthResult(
        protocolVersion = json.optInt("protocolVersion", 1),
        authMode = json.optString("authMode", "-"),
        serverName = json.optString("serverName", "MotionCam"),
        clientName = json.optString("clientName", "-"),
        capabilities = json.optJSONArray("capabilities").asStringList(),
    )
}

private fun JSONArray?.asStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optString(index))
        }
    }
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null
