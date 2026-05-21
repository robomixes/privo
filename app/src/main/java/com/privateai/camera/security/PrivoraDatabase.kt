// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context
import android.util.Base64
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

class PrivoraDatabase private constructor(context: Context, crypto: CryptoManager) {

    companion object {
        private const val TAG = "PrivoraDatabase"
        private const val DB_NAME = "privora.db"
        private const val DB_VERSION = 1

        @Volatile private var instance: PrivoraDatabase? = null

        fun getInstance(context: Context, crypto: CryptoManager): PrivoraDatabase {
            return instance ?: synchronized(this) {
                instance ?: PrivoraDatabase(context.applicationContext, crypto).also { instance = it }
            }
        }

        fun closeInstance() {
            instance?.close()
            instance = null
        }
    }

    val db: SQLiteDatabase

    init {
        System.loadLibrary("sqlcipher")
        val dbDir = File(context.filesDir, "vault")
        dbDir.mkdirs()
        val dbFile = File(dbDir, DB_NAME)
        val key = Base64.encodeToString(crypto.getDekBytes(), Base64.NO_WRAP)
        db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, key, null, null, null)
        db.execSQL("PRAGMA foreign_keys = ON")
        createTables()
    }

    private fun createTables() {
        db.execSQL("""CREATE TABLE IF NOT EXISTS photo_index (
            photo_id TEXT PRIMARY KEY,
            labels TEXT,
            scores TEXT,
            feature_vector BLOB,
            blur_score REAL DEFAULT 0,
            indexed_at INTEGER DEFAULT 0,
            description TEXT DEFAULT '',
            updated_at INTEGER DEFAULT 0
        )""")

        // Migration: add description column if missing (existing installs)
        try {
            db.execSQL("ALTER TABLE photo_index ADD COLUMN description TEXT DEFAULT ''")
        } catch (_: Exception) { /* column already exists */ }

        // Migration: add updated_at column. Tracks the last time the user
        // edited a photo (rotate / crop / filter / sticker). Sort modes
        // UPDATED_DESC / UPDATED_ASC read this; 0 means "never edited"
        // and falls back to file.lastModified() (= creation date).
        try {
            db.execSQL("ALTER TABLE photo_index ADD COLUMN updated_at INTEGER DEFAULT 0")
        } catch (_: Exception) { /* column already exists */ }

        db.execSQL("""CREATE TABLE IF NOT EXISTS face_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            photo_id TEXT NOT NULL,
            face_index INTEGER DEFAULT 0,
            box BLOB,
            embedding BLOB,
            FOREIGN KEY (photo_id) REFERENCES photo_index(photo_id) ON DELETE CASCADE
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS face_identities (
            id TEXT PRIMARY KEY,
            name TEXT DEFAULT '',
            person_id TEXT,
            centroid BLOB
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS face_exclusions (
            identity_id TEXT NOT NULL,
            photo_id TEXT NOT NULL,
            PRIMARY KEY (identity_id, photo_id)
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS face_unlinked (
            person_id TEXT PRIMARY KEY
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS contacts (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            phone TEXT DEFAULT '',
            email TEXT DEFAULT '',
            notes TEXT DEFAULT '',
            photo_id TEXT,
            contact_group TEXT DEFAULT '',
            is_favorite INTEGER DEFAULT 0,
            created_at INTEGER DEFAULT 0,
            modified_at INTEGER DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT
        )""")

        // Indexes for common queries
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_face_entries_photo ON face_entries(photo_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_face_identities_person ON face_identities(person_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_name ON contacts(name)")
    }

    fun close() {
        db.close()
    }
}

// Extension functions for float array <-> BLOB conversion
fun FloatArray.toBlob(): ByteArray {
    val buf = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    forEach { buf.putFloat(it) }
    return buf.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buf = java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buf.getFloat() }
}
