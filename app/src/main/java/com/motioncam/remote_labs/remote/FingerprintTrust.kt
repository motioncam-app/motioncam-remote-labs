package com.motioncam.remote_labs.remote

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal class FingerprintTrustManager(
    certSha256: String,
) : X509TrustManager {
    private val expectedFingerprint = certSha256.normalizeFingerprint()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw java.security.cert.CertificateException("Missing server certificate.")
        val actual = leaf.encoded.sha256Fingerprint()
        if (actual != expectedFingerprint) {
            throw java.security.cert.CertificateException(
                "Certificate fingerprint mismatch. Expected $expectedFingerprint, got $actual."
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun String.normalizeFingerprint(): String =
    replace(":", "")
        .replace(" ", "")
        .uppercase()

internal fun ByteArray.sha256Fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { "%02X".format(it) }
}

