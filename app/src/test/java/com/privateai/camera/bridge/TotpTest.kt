package com.privateai.camera.bridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 6238 Appendix B reference vectors + Base32 + otpauth:// parser tests.
 *
 * Reference: https://datatracker.ietf.org/doc/html/rfc6238#appendix-B
 *
 * Note: the RFC's reference secret is the ASCII string "12345678901234567890"
 * for SHA-1; longer derivatives are used for SHA-256 and SHA-512.
 */
class TotpTest {

    private val sha1Secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
    private val sha256Secret = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
    private val sha512Secret = "1234567890123456789012345678901234567890123456789012345678901234"
        .toByteArray(Charsets.US_ASCII)

    @Test
    fun `RFC 6238 SHA1 reference vectors`() {
        // (timestamp seconds) -> expected 8-digit code
        val cases = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )
        for ((ts, expected) in cases) {
            val code = Totp.generate(sha1Secret, ts, period = 30, digits = 8, algo = Totp.Algo.SHA1)
            assertEquals("SHA1 @ t=$ts", expected, code)
        }
    }

    @Test
    fun `RFC 6238 SHA256 reference vectors`() {
        val cases = listOf(
            59L to "46119246",
            1111111109L to "68084774",
            1111111111L to "67062674",
            1234567890L to "91819424",
            2000000000L to "90698825",
            20000000000L to "77737706"
        )
        for ((ts, expected) in cases) {
            val code = Totp.generate(sha256Secret, ts, period = 30, digits = 8, algo = Totp.Algo.SHA256)
            assertEquals("SHA256 @ t=$ts", expected, code)
        }
    }

    @Test
    fun `RFC 6238 SHA512 reference vectors`() {
        val cases = listOf(
            59L to "90693936",
            1111111109L to "25091201",
            1111111111L to "99943326",
            1234567890L to "93441116",
            2000000000L to "38618901",
            20000000000L to "47863826"
        )
        for ((ts, expected) in cases) {
            val code = Totp.generate(sha512Secret, ts, period = 30, digits = 8, algo = Totp.Algo.SHA512)
            assertEquals("SHA512 @ t=$ts", expected, code)
        }
    }

    @Test
    fun `default 6-digit code is the last 6 digits of the 8-digit code`() {
        // Sanity check: 6-digit truncation = 8-digit code mod 1_000_000.
        val code8 = Totp.generate(sha1Secret, 59L, 30, 8, Totp.Algo.SHA1)
        val code6 = Totp.generate(sha1Secret, 59L, 30, 6, Totp.Algo.SHA1)
        assertEquals(code8.takeLast(6), code6)
    }

    @Test
    fun `secondsRemaining hits boundaries correctly`() {
        // At t=0, 30 seconds remain in the period.
        assertEquals(30, Totp.secondsRemaining(0, 30))
        // At t=29, 1 second remains.
        assertEquals(1, Totp.secondsRemaining(29, 30))
        // At t=30 we're at the start of a new window — 30 remain.
        assertEquals(30, Totp.secondsRemaining(30, 30))
        // Mid-period
        assertEquals(15, Totp.secondsRemaining(15, 30))
    }

    @Test
    fun `Base32 roundtrip - empty, ASCII, binary`() {
        val cases = listOf(
            ByteArray(0),
            "f".toByteArray(),
            "fo".toByteArray(),
            "foo".toByteArray(),
            "foob".toByteArray(),
            "fooba".toByteArray(),
            "foobar".toByteArray(),
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        )
        for (orig in cases) {
            val encoded = Totp.base32Encode(orig)
            val decoded = Totp.base32Decode(encoded)
            assertArrayEquals("roundtrip ${orig.size}", orig, decoded)
        }
    }

    @Test
    fun `Base32 RFC 4648 vectors`() {
        // RFC 4648 §10 test vectors
        assertEquals("MY======".trimEnd('='), Totp.base32Encode("f".toByteArray()))
        assertEquals("MZXQ====".trimEnd('='), Totp.base32Encode("fo".toByteArray()))
        assertEquals("MZXW6===".trimEnd('='), Totp.base32Encode("foo".toByteArray()))
        assertEquals("MZXW6YQ=".trimEnd('='), Totp.base32Encode("foob".toByteArray()))
        assertEquals("MZXW6YTB".trimEnd('='), Totp.base32Encode("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI".trimEnd('='), Totp.base32Encode("foobar".toByteArray()))
    }

    @Test
    fun `Base32 tolerates lowercase and padding`() {
        val expected = "foobar".toByteArray()
        assertArrayEquals(expected, Totp.base32Decode("MZXW6YTBOI"))
        assertArrayEquals(expected, Totp.base32Decode("mzxw6ytboi"))
        assertArrayEquals(expected, Totp.base32Decode("MZXW 6YTB OI"))
        assertArrayEquals(expected, Totp.base32Decode("MZXW6YTBOI======"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Base32 rejects invalid characters`() {
        Totp.base32Decode("M1XW6YTBOI") // '1' is not in alphabet
    }

    @Test
    fun `parseUri minimal otpauth URI`() {
        // GitHub-style: otpauth://totp/GitHub:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
        val uri = "otpauth://totp/GitHub:user%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        val parsed = Totp.parseUri(uri)
        assertNotNull(parsed)
        parsed!!
        assertEquals("user@example.com", parsed.label)
        assertEquals("GitHub", parsed.issuer)
        assertEquals(30, parsed.period)
        assertEquals(6, parsed.digits)
        assertEquals(Totp.Algo.SHA1, parsed.algo)
        // "JBSWY3DPEHPK3PXP" → "Hello!\xDE\xAD\xBE\xEF"
        assertEquals(10, parsed.secret.size)
    }

    @Test
    fun `parseUri without explicit issuer infers from label prefix`() {
        val uri = "otpauth://totp/Acme:alice?secret=JBSWY3DPEHPK3PXP"
        val parsed = Totp.parseUri(uri)
        assertNotNull(parsed)
        assertEquals("Acme", parsed!!.issuer)
        assertEquals("alice", parsed.label)
    }

    @Test
    fun `parseUri custom period digits algorithm`() {
        val uri = "otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&period=60&digits=8&algorithm=SHA512"
        val parsed = Totp.parseUri(uri)
        assertNotNull(parsed)
        parsed!!
        assertEquals(60, parsed.period)
        assertEquals(8, parsed.digits)
        assertEquals(Totp.Algo.SHA512, parsed.algo)
    }

    @Test
    fun `parseUri rejects non-totp scheme`() {
        assertNull(Totp.parseUri("otpauth://hotp/Test?secret=JBSWY3DPEHPK3PXP"))
        assertNull(Totp.parseUri("https://example.com/totp"))
        assertNull(Totp.parseUri(""))
    }

    @Test
    fun `parseUri rejects missing or invalid secret`() {
        assertNull(Totp.parseUri("otpauth://totp/Test?issuer=Foo"))
        assertNull(Totp.parseUri("otpauth://totp/Test?secret="))
        assertNull(Totp.parseUri("otpauth://totp/Test?secret=NOT_BASE32!"))
    }

    @Test
    fun `buildUri then parseUri roundtrip`() {
        val secret = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)  // "Hello"
        val uri = Totp.buildUri(
            label = "user@example.com",
            issuer = "Acme",
            secret = secret,
            period = 30,
            digits = 6,
            algo = Totp.Algo.SHA1
        )
        val parsed = Totp.parseUri(uri)
        assertNotNull(parsed)
        parsed!!
        assertEquals("user@example.com", parsed.label)
        assertEquals("Acme", parsed.issuer)
        assertArrayEquals(secret, parsed.secret)
    }

    @Test
    fun `buildUri preserves non-default algorithm and period`() {
        val secret = byteArrayOf(1, 2, 3, 4, 5)
        val uri = Totp.buildUri("acct", "Co", secret, period = 60, digits = 8, algo = Totp.Algo.SHA256)
        assertTrue("URI must include algorithm", uri.contains("algorithm=SHA256"))
        assertTrue("URI must include digits", uri.contains("digits=8"))
        assertTrue("URI must include period", uri.contains("period=60"))
    }
}
