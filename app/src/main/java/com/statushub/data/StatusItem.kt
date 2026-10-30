package com.statushub.data

import android.net.Uri

/**
 * Data class representing a WhatsApp status item (image or video)
 * @param uri content Uri of the status (folder-picker document or FileProvider Uri)
 * @param type The type of status (IMAGE or VIDEO)
 * @param name The name of the file
 * @param mimeType e.g. image/jpeg, video/mp4
 * @param size The size of the file in bytes
 * @param lastModified epoch millis
 * @param source WhatsApp or WhatsApp Business
 */
data class StatusItem(
    val uri: Uri,
    val type: StatusType,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val source: StatusSource
)

/**
 * Enum class representing the type of status
 */
enum class StatusType {
    IMAGE,
    VIDEO
}

/**
 * Apps whose viewed statuses we can show
 * @param scopedPath status folder on Android 11+ (inside Android/media)
 * @param legacyPath status folder on older WhatsApp versions / Android 10 and below
 */
enum class StatusSource(
    val label: String,
    val packageName: String,
    val scopedPath: String,
    val legacyPath: String
) {
    WHATSAPP(
        label = "WhatsApp",
        packageName = "com.whatsapp",
        scopedPath = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        legacyPath = "WhatsApp/Media/.Statuses"
    ),
    WHATSAPP_BUSINESS(
        label = "WhatsApp Business",
        packageName = "com.whatsapp.w4b",
        scopedPath = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
        legacyPath = "WhatsApp Business/Media/.Statuses"
    )
}
