package com.privateai.camera.security

import android.content.Context
import android.util.Log
import com.privateai.camera.bridge.Totp
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A single TOTP authenticator entry.
 *
 * Stored encrypted as `vault/totp/<id>.totp.enc` (one file per entry).
 * Lives under `vault/` so [DuressManager.executeDuress] wipes it automatically
 * and [BackupManager] includes it in `.paicbackup` exports without any extra wiring.
 */
data class TotpEntry(
    val id: String,
    val label: String,
    val issuer: String?,
    val secret: ByteArray,
    val period: Int = 30,
    val digits: Int = 6,
    val algo: Totp.Algo = Totp.Algo.SHA1,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun displayName(): String =
        if (!issuer.isNullOrBlank()) "$issuer · $label" else label

    // data class equality with ByteArray is unsound; override.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TotpEntry) return false
        return id == other.id &&
            label == other.label &&
            issuer == other.issuer &&
            secret.contentEquals(other.secret) &&
            period == other.period &&
            digits == other.digits &&
            algo == other.algo &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + (issuer?.hashCode() ?: 0)
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + period
        result = 31 * result + digits
        result = 31 * result + algo.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

class TotpRepository(private val context: Context, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "TotpRepository"
        private const val VAULT_DIR = "vault"
        private const val TOTP_DIR = "totp"
        private const val FILE_SUFFIX = ".totp.enc"
    }

    private val totpDir: File by lazy {
        File(File(context.filesDir, VAULT_DIR), TOTP_DIR).also { it.mkdirs() }
    }

    fun list(): List<TotpEntry> {
        val files = totpDir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name.endsWith(FILE_SUFFIX) }
            .mapNotNull { loadFile(it) }
            .sortedBy { it.createdAt }
    }

    fun get(id: String): TotpEntry? = loadFile(fileFor(id))

    fun add(entry: TotpEntry): TotpEntry {
        val withId = if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry
        save(withId)
        return withId
    }

    fun update(entry: TotpEntry) {
        save(entry)
    }

    fun delete(id: String): Boolean {
        val f = fileFor(id)
        return if (f.exists()) f.delete() else false
    }

    private fun fileFor(id: String) = File(totpDir, "$id$FILE_SUFFIX")

    private fun save(entry: TotpEntry) {
        val json = JSONObject().apply {
            put("id", entry.id)
            put("label", entry.label)
            put("issuer", entry.issuer ?: JSONObject.NULL)
            put("secret", Totp.base32Encode(entry.secret))
            put("period", entry.period)
            put("digits", entry.digits)
            put("algo", entry.algo.name)
            put("createdAt", entry.createdAt)
        }
        crypto.encryptToFile(json.toString().toByteArray(Charsets.UTF_8), fileFor(entry.id))
    }

    private fun loadFile(file: File): TotpEntry? {
        if (!file.exists()) return null
        return try {
            val plaintext = crypto.decryptFile(file)
            val obj = JSONObject(String(plaintext, Charsets.UTF_8))
            val secret = Totp.base32Decode(obj.getString("secret"))
            TotpEntry(
                id = obj.getString("id"),
                label = obj.getString("label"),
                issuer = if (obj.isNull("issuer")) null else obj.optString("issuer", null),
                secret = secret,
                period = obj.optInt("period", 30),
                digits = obj.optInt("digits", 6),
                algo = Totp.Algo.fromString(obj.optString("algo", "SHA1")),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode TOTP file ${file.name}: ${e.message}")
            null
        }
    }
}
