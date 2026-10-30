package com.statushub.utils

import android.Manifest
import android.os.Build

/**
 * Runtime storage permissions.
 * Only Android 10 and below need them: on Android 11+ statuses are read through the
 * folder picker grant (see StatusScanner) and saves go through MediaStore.
 */
object PermissionHelper {

    /**
     * Get the list of permissions to request based on Android version
     */
    fun getStoragePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> emptyArray()
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            // Android 9 and below write saved statuses/downloads directly to public folders
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}
