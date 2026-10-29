package com.hemadri.statushub.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.hemadri.statushub.data.StatusItem
import com.hemadri.statushub.data.StatusSource
import com.hemadri.statushub.data.StatusType
import java.io.File

/**
 * Finds viewed WhatsApp / WhatsApp Business statuses.
 *
 * Android 11+: the .Statuses folder lives in Android/media/<package> and is hidden, so normal
 * media permissions can't see it. The user grants access to that one folder once through the
 * system folder picker (Storage Access Framework); the grant is persisted across restarts.
 *
 * Android 10 and below: the folder is read directly with READ_EXTERNAL_STORAGE.
 */
object StatusScanner {

    private const val TAG = "StatusScanner"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val STATUSES_FOLDER = ".Statuses"

    /** Folder names we walk through when the user picked a parent of .Statuses */
    private val PATH_FOLDERS = setOf(
        STATUSES_FOLDER, "Media", "WhatsApp", "WhatsApp Business", "com.whatsapp", "com.whatsapp.w4b", "media"
    )

    private data class DocumentRow(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    /** True on Android 11+, where the folder picker is the only way to read statuses */
    fun usesFolderPicker(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, source: StatusSource): Boolean = try {
        context.packageManager.getPackageInfo(source.packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Installed WhatsApp apps; falls back to WhatsApp so the user can still be prompted */
    fun installedSources(context: Context): List<StatusSource> =
        StatusSource.entries.filter { isInstalled(context, it) }.ifEmpty { listOf(StatusSource.WHATSAPP) }

    /** Installed sources the user still needs to grant folder access to (Android 11+ only) */
    fun sourcesNeedingAccess(context: Context): List<StatusSource> =
        if (!usesFolderPicker()) emptyList()
        else installedSources(context).filter { grantedTreeUri(context, it) == null }

    /** Opens the folder picker directly at the source's .Statuses folder */
    fun pickerInitialUri(source: StatusSource): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:${source.scopedPath}")

    /**
     * Persists the folder the user picked.
     * @return the source the folder belongs to, or null if it isn't a WhatsApp folder
     */
    fun onFolderPicked(context: Context, treeUri: Uri): StatusSource? {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return null
        }
        val source = when {
            documentId.contains("com.whatsapp.w4b") || documentId.contains("WhatsApp Business") ->
                StatusSource.WHATSAPP_BUSINESS
            documentId.contains("com.whatsapp") || documentId.contains("WhatsApp") -> StatusSource.WHATSAPP
            else -> return null
        }
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        PreferencesHelper.setStatusTreeUri(context, source, treeUri.toString())
        Log.d(TAG, "Granted ${source.label} folder: $documentId")
        return source
    }

    private fun grantedTreeUri(context: Context, source: StatusSource): Uri? {
        val saved = PreferencesHelper.getStatusTreeUri(context, source)?.let(Uri::parse) ?: return null
        val stillGranted = context.contentResolver.persistedUriPermissions.any {
            it.uri == saved && it.isReadPermission
        }
        return saved.takeIf { stillGranted }
    }

    /**
     * Scans every accessible source, newest first
     */
    fun scan(context: Context): List<StatusItem> {
        val items = installedSources(context).flatMap { source ->
            try {
                if (usesFolderPicker()) {
                    grantedTreeUri(context, source)?.let { scanTree(context, it, source) }.orEmpty()
                } else {
                    scanLegacy(context, source)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed for ${source.label}: ${e.message}", e)
                emptyList()
            }
        }
        Log.d(TAG, "Found ${items.size} statuses")
        return items.sortedByDescending { it.lastModified }
    }

    // region Folder picker (Android 11+)

    private fun scanTree(context: Context, treeUri: Uri, source: StatusSource): List<StatusItem> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val statusesId = findStatusesFolder(context, treeUri, rootId, depth = 0) ?: run {
            Log.w(TAG, "No $STATUSES_FOLDER folder under $rootId")
            return emptyList()
        }
        return queryChildren(context, treeUri, statusesId)
            .filter { !it.isDirectory && !it.name.startsWith(".") && it.size > 0 }
            .mapNotNull { row ->
                val type = typeOf(row.mimeType, row.name) ?: return@mapNotNull null
                StatusItem(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, row.documentId),
                    type = type,
                    name = row.name,
                    mimeType = normalizedMime(row.mimeType, row.name, type),
                    size = row.size,
                    lastModified = row.lastModified,
                    source = source
                )
            }
    }

    /** The user may have picked .Statuses itself or a parent such as WhatsApp/Media */
    private fun findStatusesFolder(context: Context, treeUri: Uri, documentId: String, depth: Int): String? {
        if (documentId.substringAfterLast('/').substringAfterLast(':') == STATUSES_FOLDER) return documentId
        if (depth >= 5) return null
        val folders = queryChildren(context, treeUri, documentId)
            .filter { it.isDirectory && it.name in PATH_FOLDERS }
            .sortedByDescending { it.name == STATUSES_FOLDER }
        for (folder in folders) {
            findStatusesFolder(context, treeUri, folder.documentId, depth + 1)?.let { return it }
        }
        return null
    }

    private fun queryChildren(context: Context, treeUri: Uri, parentId: String): List<DocumentRow> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val rows = mutableListOf<DocumentRow>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += DocumentRow(
                    documentId = cursor.getString(0),
                    name = cursor.getString(1) ?: "",
                    mimeType = cursor.getString(2) ?: "",
                    size = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                    lastModified = if (cursor.isNull(4)) 0 else cursor.getLong(4)
                )
            }
        }
        return rows
    }

    // endregion

    // region Direct file access (Android 10 and below)

    @Suppress("DEPRECATION")
    private fun scanLegacy(context: Context, source: StatusSource): List<StatusItem> {
        val root = Environment.getExternalStorageDirectory()
        return listOf(File(root, source.scopedPath), File(root, source.legacyPath))
            .filter { it.isDirectory }
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
            .mapNotNull { file ->
                val type = typeOf("", file.name) ?: return@mapNotNull null
                StatusItem(
                    uri = MediaActions.uriForFile(context, file),
                    type = type,
                    name = file.name,
                    mimeType = normalizedMime("", file.name, type),
                    size = file.length(),
                    lastModified = file.lastModified(),
                    source = source
                )
            }
    }

    // endregion

    private fun extensionMime(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    private fun typeOf(mimeType: String, name: String): StatusType? {
        val mime = mimeType.ifEmpty { extensionMime(name) ?: "" }
        return when {
            mime.startsWith("image") -> StatusType.IMAGE
            mime.startsWith("video") -> StatusType.VIDEO
            else -> null
        }
    }

    private fun normalizedMime(mimeType: String, name: String, type: StatusType): String =
        mimeType.takeIf { it.startsWith("image") || it.startsWith("video") }
            ?: extensionMime(name)
            ?: if (type == StatusType.IMAGE) "image/jpeg" else "video/mp4"
}
