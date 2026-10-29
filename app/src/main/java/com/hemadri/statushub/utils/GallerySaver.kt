package com.hemadri.statushub.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.hemadri.statushub.data.DownloadedItem
import java.io.File
import java.io.FileInputStream

/**
 * Copies downloaded videos and photos into the public gallery (Movies/SaveVibe, Pictures/SaveVibe)
 * so users can find them in their Gallery / Photos app.
 */
object GallerySaver {

    private const val TAG = "GallerySaver"
    /** Album name for everything the app saves (statuses and downloads) */
    const val GALLERY_FOLDER = "SaveVibe"

    /** Folder names used by earlier versions; still shown in the Saved tab */
    val LEGACY_FOLDERS = listOf("StatusHub", "StatusSaver")

    fun mimeTypeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/mp4"

    private fun isImage(file: File) = mimeTypeOf(file).startsWith("image")

    /** Gallery-relative folder the file ends up in, e.g. "Movies/SaveVibe" */
    fun galleryFolderFor(file: File): String =
        "${if (isImage(file)) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES}/$GALLERY_FOLDER"

    /**
     * Copies [source] into Movies/SaveVibe (videos) or Pictures/SaveVibe (photos).
     * @return a content Uri for the gallery copy, or null if it could not be saved
     */
    fun saveToGallery(context: Context, source: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveUsingMediaStore(context, source)
            } else {
                saveUsingFileSystem(context, source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed: ${e.message}", e)
            null
        }
    }

    /**
     * Copies a downloaded file into the gallery and removes the private copy.
     * Falls back to the private file if the gallery copy fails (e.g. storage permission denied on Android 9-).
     */
    fun moveToGallery(context: Context, file: File): DownloadedItem {
        val mimeType = mimeTypeOf(file)
        val galleryUri = saveToGallery(context, file)
        return if (galleryUri != null) {
            file.delete()
            DownloadedItem(galleryUri, mimeType, "${galleryFolderFor(file)}/${file.name}", savedToGallery = true)
        } else {
            DownloadedItem(MediaActions.uriForFile(context, file), mimeType, file.absolutePath, savedToGallery = false)
        }
    }

    private fun saveUsingMediaStore(context: Context, source: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(source))
            put(MediaStore.MediaColumns.RELATIVE_PATH, galleryFolderFor(source))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isImage(source)) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(source).use { it.copyTo(out) }
            } ?: throw IllegalStateException("Could not open output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveUsingFileSystem(context: Context, source: File): Uri? {
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStorageDirectory(), galleryFolderFor(source))
        if (!folder.exists() && !folder.mkdirs()) return null
        val destination = File(folder, source.name)
        source.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(mimeTypeOf(destination)),
            null
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
    }
}
