package com.hemadri.statushub.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.hemadri.statushub.data.StatusItem
import com.hemadri.statushub.data.StatusType
import com.hemadri.statushub.utils.GallerySaver
import com.hemadri.statushub.utils.StatusScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository class for managing status files
 * Handles scanning and saving operations
 */
class StatusRepository(private val context: Context) {

    companion object {
        private const val TAG = "StatusRepository"
        /** Saved statuses share the app album: Pictures/SaveVibe and Movies/SaveVibe */
        private const val SAVE_FOLDER = GallerySaver.GALLERY_FOLDER
    }

    /**
     * Scan WhatsApp / WhatsApp Business status folders for images and videos, newest first
     */
    suspend fun scanStatusFiles(): List<StatusItem> = withContext(Dispatchers.IO) {
        StatusScanner.scan(context)
    }

    /**
     * Save a status to the gallery
     * @return true if saved successfully, false otherwise
     */
    suspend fun saveStatusFile(statusItem: StatusItem): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveUsingMediaStore(statusItem)
            } else {
                saveUsingFileSystem(statusItem)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving status: ${e.message}", e)
            false
        }
    }

    private fun saveUsingMediaStore(statusItem: StatusItem): Boolean {
        val resolver = context.contentResolver
        val isImage = statusItem.type == StatusType.IMAGE
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, statusItem.name)
            put(MediaStore.MediaColumns.MIME_TYPE, statusItem.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES}/$SAVE_FOLDER"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isImage) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            val input = resolver.openInputStream(statusItem.uri) ?: throw IllegalStateException("Cannot read status")
            input.use { source ->
                resolver.openOutputStream(uri)?.use { out -> source.copyTo(out) }
                    ?: throw IllegalStateException("Cannot open output stream")
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveUsingFileSystem(statusItem: StatusItem): Boolean {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$SAVE_FOLDER/${if (statusItem.type == StatusType.IMAGE) "Images" else "Videos"}"
        )
        if (!folder.exists() && !folder.mkdirs()) return false
        val destination = File(folder, statusItem.name)
        val input = context.contentResolver.openInputStream(statusItem.uri) ?: return false
        input.use { source -> destination.outputStream().use { source.copyTo(it) } }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(statusItem.mimeType), null)
        return true
    }
}
