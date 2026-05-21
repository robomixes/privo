// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VaultFolder(
    val id: String,
    val name: String,
    val parentId: String?, // null = root level
    val createdAt: Long
)

/**
 * Manages custom folders in the vault.
 * Folder metadata is stored as encrypted JSON.
 * Folder contents are stored as encrypted files in subdirectories.
 */
class FolderManager(private val context: Context, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "FolderManager"
        private const val FOLDERS_DIR = "vault/folders"
        private const val INDEX_FILE = "_index.json.enc"
        private const val MAX_DEPTH = 3
    }

    private val foldersRoot: File by lazy {
        File(context.filesDir, FOLDERS_DIR).also { it.mkdirs() }
    }

    private val indexFile: File get() = File(foldersRoot, INDEX_FILE)

    private fun loadIndex(): MutableList<VaultFolder> {
        if (!indexFile.exists()) return mutableListOf()
        return try {
            val decrypted = crypto.decryptFile(indexFile)
            val json = String(decrypted, Charsets.UTF_8)
            val arr = JSONArray(json)
            val list = mutableListOf<VaultFolder>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(VaultFolder(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    parentId = obj.optString("parent", "").ifEmpty { null },
                    createdAt = obj.getLong("created")
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load folder index: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveIndex(folders: List<VaultFolder>) {
        val arr = JSONArray()
        for (f in folders) {
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("parent", f.parentId ?: "")
                put("created", f.createdAt)
            })
        }
        crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), indexFile)
    }

    fun listAllFolders(): List<VaultFolder> = loadIndex()

    fun listRootFolders(): List<VaultFolder> =
        loadIndex().filter { it.parentId == null }.sortedBy { it.name }

    fun listSubfolders(parentId: String): List<VaultFolder> =
        loadIndex().filter { it.parentId == parentId }.sortedBy { it.name }

    fun getFolder(id: String): VaultFolder? =
        loadIndex().find { it.id == id }

    fun createFolder(name: String, parentId: String?): VaultFolder {
        // Check depth limit
        if (parentId != null) {
            val depth = getFolderDepth(parentId)
            if (depth >= MAX_DEPTH - 1) {
                throw IllegalStateException("Maximum folder depth ($MAX_DEPTH) reached")
            }
        }

        val folders = loadIndex()

        // Handle name collision
        val existingNames = folders.filter { it.parentId == parentId }.map { it.name }
        var finalName = name
        var counter = 2
        while (finalName in existingNames) {
            finalName = "$name ($counter)"
            counter++
        }

        val folder = VaultFolder(
            id = UUID.randomUUID().toString().take(12),
            name = finalName,
            parentId = parentId,
            createdAt = System.currentTimeMillis()
        )

        // Create directory on disk
        getFolderDir(folder.id).mkdirs()

        folders.add(folder)
        saveIndex(folders)
        Log.d(TAG, "Folder created: ${folder.name} (${folder.id})")
        return folder
    }

    fun renameFolder(id: String, newName: String) {
        val folders = loadIndex()
        val idx = folders.indexOfFirst { it.id == id }
        if (idx >= 0) {
            folders[idx] = folders[idx].copy(name = newName)
            saveIndex(folders)
            Log.d(TAG, "Folder renamed: $id → $newName")
        }
    }

    fun deleteFolder(id: String) {
        val folders = loadIndex()

        // Collect all folder IDs to delete (this folder + all descendants)
        val toDelete = mutableSetOf(id)
        fun collectChildren(parentId: String) {
            folders.filter { it.parentId == parentId }.forEach {
                toDelete.add(it.id)
                collectChildren(it.id)
            }
        }
        collectChildren(id)

        // Delete directories
        for (fId in toDelete) {
            val dir = getFolderDir(fId)
            if (dir.exists()) dir.deleteRecursively()
        }

        // Remove from index
        val remaining = folders.filter { it.id !in toDelete }
        saveIndex(remaining)
        Log.d(TAG, "Folder deleted: $id (${toDelete.size} folders total)")
    }

    /**
     * Get the breadcrumb path from root to this folder.
     */
    fun getFolderPath(id: String): List<VaultFolder> {
        val folders = loadIndex()
        val path = mutableListOf<VaultFolder>()
        var current = folders.find { it.id == id }
        while (current != null) {
            path.add(0, current)
            current = if (current.parentId != null) folders.find { it.id == current!!.parentId } else null
        }
        return path
    }

    /**
     * Count items (encrypted files) in a folder, including all subfolders recursively.
     */
    fun countItems(folderId: String): Int {
        val dir = getFolderDir(folderId)
        val directCount = if (dir.exists()) {
            // Mirror VaultRepository.listFolderItems's filter (the canonical
            // "is a user-visible item" rule). Previously this only excluded
            // .thumb.enc / .vid.thumb.enc, so every photo's .meta.enc EXIF
            // sidecar was being counted as a second photo — folder count
            // appeared doubled after any import with EXIF metadata. Now also
            // excludes .meta.enc / .ocr.enc, and treats .vid.enc / .pdf.enc /
            // .file.enc as their own item types (still counted, but only once).
            (dir.listFiles() ?: emptyArray()).count {
                it.isFile && it.name.endsWith(".enc") &&
                    !it.name.endsWith(".thumb.enc") &&
                    !it.name.endsWith(".vid.thumb.enc") &&
                    !it.name.endsWith(".meta.enc") &&
                    !it.name.endsWith(".ocr.enc") &&
                    !it.name.startsWith("_tobedeleted_")
            }
        } else 0
        val subCount = listSubfolders(folderId).sumOf { countItems(it.id) }
        return directCount + subCount
    }

    /**
     * Count subfolders of a folder.
     */
    fun countSubfolders(folderId: String): Int =
        loadIndex().count { it.parentId == folderId }

    /**
     * Get the disk directory for a folder.
     */
    fun getFolderDir(folderId: String): File =
        File(foldersRoot, folderId).also { it.mkdirs() }

    private fun getFolderDepth(folderId: String): Int {
        val folders = loadIndex()
        var depth = 0
        var current = folders.find { it.id == folderId }
        while (current?.parentId != null) {
            depth++
            current = folders.find { it.id == current!!.parentId }
        }
        return depth
    }
}
