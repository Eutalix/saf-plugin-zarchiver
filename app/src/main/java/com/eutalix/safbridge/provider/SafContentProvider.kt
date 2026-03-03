package com.eutalix.safbridge.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException

/**
 * The core bridge between ZArchiver and Android's Storage Access Framework.
 * 
 * Protocol details (Reverse Engineered from ZArchiver Pro):
 * 1. ZArchiver queries "get=accounts" to list roots (mapped to our SharedPreferences).
 * 2. ZArchiver uses 'call' method for FS operations (mkdir, rename, remove).
 * 3. ZArchiver uses 'openFile' with custom modes (w2, etc) for reading/writing.
 */
class SafContentProvider : ContentProvider() {

    // Columns expected by ZArchiver's internal cursor adapter
    private val COL_ACCOUNTS = arrayOf("_name", "_id", "_flags")
    private val COL_FILES = arrayOf("_name", "_id", "_size", "_dir", "_last_mod", "_path")

    // Security: Packages allowed to perform potentially destructive edits (overwrite)
    private val ALLOWED_WRITE_PACKAGES = setOf(
        "ru.zdevs.zarchiver.pro",
        "com.eutalix.safbridge" // Allow self for internal debugging
    )

    override fun onCreate(): Boolean = true

    /**
     * Fixes malformed URIs sometimes sent by the client or stored incorrectly.
     * E.g., converts "content:/com.android..." to "content://com.android..."
     */
    private fun parseRootUri(uriString: String): Uri {
        var fixedString = uriString
        if (fixedString.startsWith("content:/") && !fixedString.startsWith("content://")) {
            fixedString = fixedString.replaceFirst("content:/", "content://")
        }
        return Uri.parse(fixedString)
    }

    /**
     * Checks if the calling package is the Pro version or Self.
     * Used to prevent file corruption on existing files with the Free version.
     */
    private fun isProVersion(): Boolean {
        val settings = context?.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (settings?.getBoolean("force_readonly", false) == true) return false

        val callingPkg = callingPackage
        if (callingPkg == null) return false
        return ALLOWED_WRITE_PACKAGES.contains(callingPkg)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null

        // 1. Handshake: List "Accounts" (Roots)
        if (selection == "get=accounts") {
            val cursor = MatrixCursor(COL_ACCOUNTS)
            val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
            prefs.all.forEach { (uriString, name) ->
                // _id is the SAF Tree URI string itself
                cursor.addRow(arrayOf(name.toString(), uriString, 0))
            }
            return cursor
        }

        // 2. List Files in a directory
        try {
            val accountUriString = uri.fragment ?: return null
            val rootUri = parseRootUri(accountUriString)
            val path = Uri.decode(uri.path ?: "/")
            
            val targetDoc = findDocByPath(context, rootUri, path) ?: return null
            if (!targetDoc.isDirectory) return null

            val cursor = MatrixCursor(COL_FILES)
            targetDoc.listFiles().forEach { file ->
                val visualPath = "${uri.path ?: ""}/${file.name}".replace("//", "/")
                
                // CRITICAL: ZArchiver needs a valid size > 0 to attempt opening text files.
                val size = file.length()
                
                cursor.addRow(arrayOf(
                    file.name ?: "Unknown",
                    file.uri.toString(),
                    size, 
                    if (file.isDirectory) 1 else 0,
                    file.lastModified(),
                    visualPath
                ))
            }
            return cursor
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val response = Bundle().apply { putInt("_ret", 1) } // Default: Error
        val context = context ?: return response
        
        // 'flush' is called when closing a file stream. Always acknowledge to prevent IOExceptions.
        if (method == "flush") { 
            response.putInt("_ret", 0)
            return response 
        }

        // NOTE: We do NOT block 'mkdir', 'rename', or 'remove' for the Free version anymore.
        // The Free version handles these operations correctly.

        val rootUri = parseRootUri(extras?.getString("_account") ?: return response)
        val logicalPath = Uri.decode(extras.getParcelable<Uri>("_uri")?.path ?: "/")

        try {
            when (method) {
                "mkdir" -> {
                    val name = extras.getString("_name")
                    if (name != null) {
                        val parent = findDocByPath(context, rootUri, logicalPath)
                        if (parent != null && parent.isDirectory) {
                            // If exists or created successfully, return success
                            if (parent.findFile(name) != null || parent.createDirectory(name) != null) 
                                response.putInt("_ret", 0)
                        }
                    }
                }
                "rename" -> {
                    val newName = extras.getString("_name")
                    if (newName != null) {
                        val source = findDocByPath(context, rootUri, logicalPath)
                        if (source != null) {
                            val parentPath = if (logicalPath.lastIndexOf('/') > 0) logicalPath.substring(0, logicalPath.lastIndexOf('/')) else "/"
                            val parent = findDocByPath(context, rootUri, parentPath)
                            
                            // FIX: SAF rename fails if destination exists. ZArchiver expects overwrite.
                            // We must manually find and delete the collision first.
                            val collision = parent?.findFile(newName)
                            if (collision != null && collision.uri != source.uri) collision.delete()
                            
                            if (source.renameTo(newName)) response.putInt("_ret", 0)
                        }
                    }
                }
                "remove" -> {
                    val doc = findDocByPath(context, rootUri, logicalPath)
                    if (doc != null && doc.delete()) response.putInt("_ret", 0)
                }
                "disk" -> {
                    // Mock disk space to prevent "Disk Full" errors in the UI
                    response.putLong("_used", 0)
                    response.putLong("_free", 1024L * 1024L * 1024L * 100L) // 100GB Free
                    response.putString("_fs_type", "SAF")
                    response.putInt("_ret", 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return response
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: throw FileNotFoundException("Ctx")
        val rootUri = parseRootUri(uri.fragment ?: throw FileNotFoundException("No Account"))
        val path = Uri.decode(uri.path ?: throw FileNotFoundException("No Path"))

        var doc = findDocByPath(context, rootUri, path)

        // Sanitize Mode: ZArchiver sends custom modes like "w2", "w4". 
        // We force "wt" (Write Truncate) to ensure clean writes, or "r" for reading.
        val safeMode = if (mode.contains("w")) "wt" else "r"
        val isWrite = mode.contains("w") || mode.contains("+")

        // 1. Existing File Logic
        if (doc != null && doc.exists()) {
            // SECURITY CHECK: If it's an existing file AND we are trying to write...
            if (isWrite && !isProVersion()) {
                // ... we insist on Pro version to avoid corruption/truncation issues.
                throw SecurityException("ZArchiver Free cannot edit existing files in this plugin.")
            }
            return context.contentResolver.openFileDescriptor(doc.uri, safeMode)
        }
        
        // 2. New File Logic (Create)
        // We ALLOW creating new files for everyone (Free & Pro).
        if (isWrite) {
            val parentPath = if (path.lastIndexOf('/') > 0) path.substring(0, path.lastIndexOf('/')) else "/"
            val name = path.substring(path.lastIndexOf('/') + 1)
            val parent = findDocByPath(context, rootUri, parentPath) ?: throw FileNotFoundException("No parent")
            
            val mime = getMimeType(name)
            val newFile = parent.createFile(mime, name) ?: throw FileNotFoundException("Fail create")
            return context.contentResolver.openFileDescriptor(newFile.uri, safeMode)
        }

        throw FileNotFoundException("File not found: $path")
    }

    /**
     * Navigates the SAF tree using path segments.
     * Necessary because SAF uses opaque IDs, but ZArchiver uses UNIX-style paths.
     */
    private fun findDocByPath(context: Context, rootUri: Uri, path: String): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        if (path == "/" || path.isEmpty()) return current
        
        path.split("/").filter { it.isNotEmpty() }.forEach { part ->
            current = current.findFile(part) ?: return null
        }
        return current
    }
    
    private fun getMimeType(n: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(n)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    // Required boilerplate methods (not used by ZArchiver)
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}