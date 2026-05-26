package com.motioncam.remote_labs.remote

import org.json.JSONException
import org.json.JSONObject

data class PairingDetails(
    val name: String = "MotionCam",
    val url: String,
    val pairingCode: String? = null,
    val pairingToken: String? = null,
    val expiresAt: Long? = null,
    val certSha256: String? = null,
) {
    val isExpired: Boolean
        get() = expiresAt?.let { it <= System.currentTimeMillis() } == true
}

fun parsePairingDetails(input: String): Result<PairingDetails> {
    val trimmed = input.trim()
    if (!trimmed.startsWith("{")) {
        return Result.failure(IllegalArgumentException("Paste a MotionCam QR payload or enter URL and code manually."))
    }

    return try {
        val json = JSONObject(trimmed)
        val version = json.optInt("v", -1)
        if (version != 1) {
            return Result.failure(IllegalArgumentException("Unsupported pairing payload version: $version"))
        }

        val url = json.getString("url").trim()
        val pairingCode = json.optString("pairingCode", "").trim().ifBlank { null }
        val pairingToken = json.optString("pairingToken", "").trim().ifBlank { null }
        if (url.isBlank() || (pairingCode == null && pairingToken == null)) {
            return Result.failure(IllegalArgumentException("Pairing payload is missing URL or pairing secret."))
        }

        Result.success(
            PairingDetails(
                name = json.optString("name", "MotionCam").ifBlank { "MotionCam" },
                url = url,
                pairingCode = pairingCode,
                pairingToken = pairingToken,
                expiresAt = if (json.has("expiresAt")) json.optLong("expiresAt") else null,
                certSha256 = json.optString("certSha256", "").ifBlank { null },
            )
        )
    } catch (e: JSONException) {
        Result.failure(IllegalArgumentException("Invalid MotionCam pairing payload.", e))
    }
}
