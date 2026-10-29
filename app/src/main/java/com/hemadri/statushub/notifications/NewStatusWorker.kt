package com.hemadri.statushub.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hemadri.statushub.MainActivity
import com.hemadri.statushub.R
import com.hemadri.statushub.analytics.Analytics
import com.hemadri.statushub.repository.StatusRepository
import com.hemadri.statushub.utils.PermissionHelper
import com.hemadri.statushub.utils.PreferencesHelper
import com.hemadri.statushub.utils.StatusScanner
import com.hemadri.statushub.widget.StatusWidgetProvider
import java.util.concurrent.TimeUnit

/**
 * Periodically looks for statuses WhatsApp downloaded since the last check:
 * auto-saves them (if enabled), posts a "new statuses" notification and refreshes the widget.
 */
class NewStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!canReadStatuses(app)) return Result.success()

        val items = StatusScanner.scan(app)
        val newest = items.maxOfOrNull { it.lastModified } ?: return Result.success()
        val lastProcessed = PreferencesHelper.getLastProcessedStatusAt(app)

        // First run only sets the baseline, so existing statuses don't trigger a flood
        if (lastProcessed == 0L) {
            PreferencesHelper.setLastProcessedStatusAt(app, newest)
        } else {
            val fresh = items.filter { it.lastModified > lastProcessed }
            if (fresh.isNotEmpty()) {
                val autoSave = PreferencesHelper.isAutoSaveEnabled(app)
                val saved = if (autoSave) {
                    val repository = StatusRepository(app)
                    fresh.count { repository.saveStatusFile(it) }
                } else 0
                if (PreferencesHelper.isNewStatusAlertsEnabled(app)) notify(app, fresh.size, saved)
                PreferencesHelper.setLastProcessedStatusAt(app, newest)
            }
        }

        val lastSeen = PreferencesHelper.getLastSeenStatusAt(app)
        PreferencesHelper.setNewStatusCount(app, items.count { it.lastModified > lastSeen })
        StatusWidgetProvider.updateAll(app)
        return Result.success()
    }

    private fun canReadStatuses(context: Context): Boolean =
        if (StatusScanner.usesFolderPicker()) {
            StatusScanner.sourcesNeedingAccess(context).size < StatusScanner.installedSources(context).size
        } else {
            PermissionHelper.getStoragePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    private fun notify(context: Context, newCount: Int, savedCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val res = context.resources
        val title = if (savedCount > 0) {
            res.getQuantityString(R.plurals.notification_auto_saved_title, savedCount, savedCount)
        } else {
            res.getQuantityString(R.plurals.notification_new_statuses_title, newCount, newCount)
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_new_statuses_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Analytics.log(Analytics.NEW_STATUS_NOTIFICATION, "count" to newCount, "auto_saved" to savedCount)
    }

    companion object {
        private const val WORK_NAME = "new_status_check"
        private const val CHANNEL_ID = "new_statuses"
        private const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            createChannel(context)
            val request = PeriodicWorkRequestBuilder<NewStatusWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<NewStatusWorker>().build())
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
