package com.statushub.utils

import com.statushub.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Open / share / repost actions for saved and downloaded media
 */
object MediaActions {

    private const val TAG = "MediaActions"
    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun open(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No app to open $mimeType", e)
            Toast.makeText(context, R.string.media_no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, uri: Uri, mimeType: String) = share(context, listOf(uri to mimeType))

    /** Shares one or more files (e.g. every photo/video of an Instagram carousel) */
    fun share(context: Context, items: List<Pair<Uri, String>>) {
        if (items.isEmpty()) return
        val chooser = Intent.createChooser(sendIntent(items), context.getString(R.string.media_share_via)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Sends the media straight to WhatsApp (or WhatsApp Business) so the user can
     * post it as their own status or forward it to a chat.
     */
    fun repostToWhatsApp(context: Context, uri: Uri, mimeType: String) =
        repostToWhatsApp(context, listOf(uri to mimeType))

    fun repostToWhatsApp(context: Context, items: List<Pair<Uri, String>>) {
        if (items.isEmpty()) return
        val installed = WHATSAPP_PACKAGES.firstOrNull { isInstalled(context, it) }
        if (installed == null) {
            Toast.makeText(context, R.string.media_whatsapp_not_installed, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = sendIntent(items).apply {
            setPackage(installed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Repost to $installed failed", e)
            share(context, items)
        }
    }

    private fun sendIntent(items: List<Pair<Uri, String>>): Intent {
        val mimeTypes = items.map { it.second }.distinct()
        val mimeType = when {
            mimeTypes.size == 1 -> mimeTypes.first()
            mimeTypes.all { it.startsWith("image") } -> "image/*"
            mimeTypes.all { it.startsWith("video") } -> "video/*"
            else -> "*/*"
        }
        return if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, items.first().first)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.first }))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}
