package com.statushub.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.statushub.R
import com.statushub.ads.PRIVACY_POLICY_URL
import com.statushub.analytics.Analytics
import com.statushub.notifications.NewStatusWorker
import com.statushub.utils.Engagement
import com.statushub.utils.PreferencesHelper

/**
 * Header overflow menu: status alerts, auto-save, share, rate, language and privacy
 */
@Composable
fun SettingsMenuButton() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(PreferencesHelper.isNewStatusAlertsEnabled(context)) }
    var autoSave by remember { mutableStateOf(PreferencesHelper.isAutoSaveEnabled(context)) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.log(Analytics.NOTIFICATIONS_PERMISSION, "granted" to granted, "from" to "settings")
        alerts = granted
        PreferencesHelper.setNewStatusAlertsEnabled(context, granted)
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.settings_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_new_status_alerts)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                trailingIcon = { Checkbox(checked = alerts, onCheckedChange = null) },
                onClick = {
                    val enable = !alerts
                    val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        alerts = enable
                        PreferencesHelper.setNewStatusAlertsEnabled(context, enable)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_auto_save)) },
                leadingIcon = { Icon(Icons.Rounded.AutoMode, contentDescription = null) },
                trailingIcon = { Checkbox(checked = autoSave, onCheckedChange = null) },
                onClick = {
                    autoSave = !autoSave
                    PreferencesHelper.setAutoSaveEnabled(context, autoSave)
                    Analytics.log(Analytics.AUTO_SAVE_TOGGLED, "enabled" to autoSave)
                    if (autoSave) NewStatusWorker.runNow(context)
                }
            )
            HorizontalDivider()
            MenuAction(Icons.Rounded.Share, stringResource(R.string.settings_share_app)) {
                open = false
                Engagement.shareApp(context)
            }
            MenuAction(Icons.Rounded.StarRate, stringResource(R.string.settings_rate_app)) {
                open = false
                Engagement.openStoreListing(context)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                MenuAction(Icons.Rounded.Language, stringResource(R.string.settings_language)) {
                    open = false
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }
            MenuAction(Icons.Rounded.Policy, stringResource(R.string.settings_privacy_policy)) {
                open = false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            }
        }
    }
}

@Composable
private fun MenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}
