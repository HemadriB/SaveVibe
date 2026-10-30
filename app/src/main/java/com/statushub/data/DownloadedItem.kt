package com.statushub.data

import android.net.Uri

/**
 * A downloaded file, ready to open/share/repost
 */
data class DownloadedItem(
    val uri: Uri,
    val mimeType: String,
    /** Where the user can find it, e.g. "Movies/SaveVibe/name.mp4" */
    val location: String,
    val savedToGallery: Boolean
) {
    val isVideo: Boolean get() = mimeType.startsWith("video")
}
