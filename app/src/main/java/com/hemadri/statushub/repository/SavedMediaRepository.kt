package com.hemadri.statushub.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.hemadri.statushub.utils.GallerySaver
import com.hemadri.statushub.utils.MediaActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A file the user saved from statuses or downloaded with the video downloader
 */
data class SavedMedia(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateModified: Long
) {
    val isVideo: Boolean get() = mimeType.startsWith("video")
}

/**
 * Lists everything the app has saved to the gallery: Pictures|Movies/SaveVibe, plus the
 * StatusSaver / StatusHub folders used by earlier versions
 */
class SavedMediaRepository(private val context: Context) {

    companion object {
        private const val TAG = "SavedMediaRepository"
        private val ALL_FOLDERS = listOf(GallerySaver.GALLERY_FOLDER) + GallerySaver.LEGACY_FOLDERS
    }

    suspend fun loadSavedMedia(): List<SavedMedia> = withContext(Dispatchers.IO) {
        val items = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStore(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ALL_FOLDERS.map { "${Environment.DIRECTORY_PICTURES}/$it%" }
                ) + queryMediaStore(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    ALL_FOLDERS.map { "${Environment.DIRECTORY_MOVIES}/$it%" }
                )
            } else {
                listLegacyFolders()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadSavedMedia failed: ${e.message}", e)
            emptyList()
        }
        items.sortedByDescending { it.dateModified }
    }

    /**
     * Deletes a saved item. Only works for files created by this install of the app.
     */
    suspend fun delete(item: SavedMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(item.uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "delete failed: ${e.message}", e)
            false
        }
    }

    private fun queryMediaStore(collection: Uri, relativePathPatterns: List<String>): List<SavedMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = relativePathPatterns.joinToString(" OR ") {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        }
        val result = mutableListOf<SavedMedia>()
        context.contentResolver.query(
            collection, projection, selection, relativePathPatterns.toTypedArray(), null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                result.add(
                    SavedMedia(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                        name = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "",
                        dateModified = cursor.getLong(dateCol) * 1000
                    )
                )
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun listLegacyFolders(): List<SavedMedia> {
        // Matches StatusRepository.saveUsingFileSystem and GallerySaver.saveUsingFileSystem
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val folders = ALL_FOLDERS.flatMap { name ->
            listOf(
                File(downloads, "$name/Images"),
                File(downloads, "$name/Videos"),
                File(movies, name),
                File(pictures, name)
            )
        }
        return folders.flatMap { folder -> folder.listFiles()?.filter { it.isFile }.orEmpty() }
            .mapNotNull { file ->
                val mime = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase()) ?: return@mapNotNull null
                if (!mime.startsWith("image") && !mime.startsWith("video")) return@mapNotNull null
                SavedMedia(
                    uri = MediaActions.uriForFile(context, file),
                    name = file.name,
                    mimeType = mime,
                    dateModified = file.lastModified()
                )
            }
    }
}
