package com.privateai.camera.bridge

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 (TOTP) generator + RFC 4648 Base32 decoder + otpauth:// URI parser.
 *
 * Pure Kotlin, no Android deps — unit-testable against RFC 6238 Appendix B.
 */
object Totp {

    enum class Algo(val mac: String) {
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512");

        companion object {
            fun fromString(s: String?): Algo = when (s?.uppercase()) {
                "SHA256" -> SHA256
                "SHA512" -> SHA512
                else -> SHA1
            }
        }
    }

    /**
     * Parsed otpauth:// URI. Caller maps these into [TotpEntry].
     */
    data class ParsedUri(
        val label: String,
        val issuer: String?,
        val secret: ByteArray,
        val period: Int,
        val digits: Int,
        val algo: Algo
    )

    /**
     * Generate the current TOTP code for a given counter value.
     * counter = floor(unixTime / period)
     */
    fun generate(
        secret: ByteArray,
        timestamp: Long,
        period: Int = 30,
        digits: Int = 6,
        algo: Algo = Algo.SHA1
    ): String {
        require(period > 0) { "period must be positive" }
        require(digits in 6..10) { "digits must be 6..10" }
        val counter = timestamp / period
        return generateForCounter(secret, counter, digits, algo)
    }

    fun generateForCounter(
        secret: ByteArray,
        counter: Long,
        digits: Int,
        algo: Algo
    ): String {
        val msg = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array()
        val mac = Mac.getInstance(algo.mac)
        mac.init(SecretKeySpec(secret, algo.mac))
        val hash = mac.doFinal(msg)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        val code = binary % modulus
        return code.toString().padStart(digits, '0')
    }

    /**
     * Seconds remaining in the current period at [timestamp].
     * Useful for the countdown ring in the UI.
     */
    fun secondsRemaining(timestamp: Long, period: Int = 30): Int =
        (period - (timestamp % period).toInt()).let { if (it == 0) period else it }

    // ========== Base32 (RFC 4648) ==========

    private const val B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Decode an RFC 4648 Base32 string (A–Z, 2–7) into raw bytes.
     * Tolerates lowercase, whitespace, and "=" padding. Throws on invalid chars.
     */
    fun base32Decode(input: String): ByteArray {
        val cleaned = input.trim().uppercase().filter { it != ' ' && it != '\t' && it != '-' && it != '=' }
        if (cleaned.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (c in cleaned) {
            val v = B32_ALPHABET.indexOf(c)
            require(v >= 0) { "Invalid Base32 character: '$c'" }
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.write((buffer shr bitsLeft) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun base32Encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in input) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(B32_ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(B32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    // ========== otpauth:// parser ==========

    /**
     * Parse `otpauth://totp/{issuer:label}?secret=...&issuer=...&algorithm=...&digits=...&period=...`
     *
     * Tolerates URL-encoded labels, missing optional params (defaults per RFC 6238),
     * and the common `Issuer:Account` label convention.
     */
    fun parseUri(uri: String): ParsedUri? {
        if (!uri.startsWith("otpauth://totp/", ignoreCase = true)) return null

        val withoutScheme = uri.removePrefix("otpauth://").let {
            if (it.startsWith("totp/", ignoreCase = true)) it.substring(5) else return null
        }
        val qIdx = withoutScheme.indexOf('?')
        val labelRaw = if (qIdx >= 0) withoutScheme.substring(0, qIdx) else withoutScheme
        val query = if (qIdx >= 0) withoutScheme.substring(qIdx + 1) else ""

        val params = query.split('&').asSequence()
            .filter { it.isNotEmpty() }
            .map { kv ->
                val eq = kv.indexOf('=')
                if (eq < 0) kv to ""
                else kv.substring(0, eq) to URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
            }
            .toMap()

        val secretStr = params["secret"]?.takeIf { it.isNotBlank() } ?: return null
        val secret = try { base32Decode(secretStr) } catch (_: Exception) { return null }
        if (secret.isEmpty()) return null

        val decodedLabel = URLDecoder.decode(labelRaw, "UTF-8")
        val (issuerFromLabel, accountLabel) = if (decodedLabel.contains(':')) {
            val parts = decodedLabel.split(':', limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            null to decodedLabel.trim()
        }
        val issuer = params["issuer"]?.takeIf { it.isNotBlank() } ?: issuerFromLabel

        return ParsedUri(
            label = accountLabel,
            issuer = issuer,
            secret = secret,
            period = params["period"]?.toIntOrNull() ?: 30,
            digits = params["digits"]?.toIntOrNull() ?: 6,
            algo = Algo.fromString(params["algorithm"])
        )
    }

    /**
     * Build an otpauth:// URI for sharing/exporting a TOTP entry as a QR code.
     */
    fun buildUri(
        label: String,
        issuer: String?,
        secret: ByteArray,
        period: Int = 30,
        digits: Int = 6,
        algo: Algo = Algo.SHA1
    ): String {
        val labelPart = if (!issuer.isNullOrBlank()) "${urlEnc(issuer)}:${urlEnc(label)}"
                       else urlEnc(label)
        val sb = StringBuilder("otpauth://totp/$labelPart?secret=${base32Encode(secret)}")
        if (!issuer.isNullOrBlank()) sb.append("&issuer=${urlEnc(issuer)}")
        if (algo != Algo.SHA1) sb.append("&algorithm=${algo.name}")
        if (digits != 6) sb.append("&digits=$digits")
        if (period != 30) sb.append("&period=$period")
        return sb.toString()
    }

    private fun urlEnc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
