package com.statushub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.statushub.MainActivity
import com.statushub.R
import com.statushub.notifications.NewStatusWorker
import com.statushub.utils.PreferencesHelper

/**
 * Home-screen widget: shows how many new statuses are waiting and opens the app on tap.
 * The count is written by [NewStatusWorker] and reset when the user opens the Status tab.
 */
class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onEnabled(context: Context) {
        NewStatusWorker.runNow(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val count = PreferencesHelper.getNewStatusCount(context)
            val title = if (count > 0) {
                context.resources.getQuantityString(R.plurals.widget_new_statuses, count, count)
            } else {
                context.getString(R.string.widget_no_new_statuses)
            }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return RemoteViews(context.packageName, R.layout.widget_status).apply {
                setTextViewText(R.id.widget_title, title)
                setOnClickPendingIntent(R.id.widget_root, openApp)
            }
        }
    }
}
