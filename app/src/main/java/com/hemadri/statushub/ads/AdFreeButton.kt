package com.hemadri.statushub.ads

import com.hemadri.statushub.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemadri.statushub.ui.components.GradientButton
import com.hemadri.statushub.ui.components.IconBadge
import com.hemadri.statushub.ui.components.pulse
import com.hemadri.statushub.ui.theme.BrandGradients
import kotlinx.coroutines.delay

/** Hosted on Firebase Hosting (project statushub-2ad1c, site savevibe-app) */
const val PRIVACY_POLICY_URL = "https://savevibe-app.web.app/privacy-policy"

/**
 * Header action: opens the optional "watch a short video → no ads for 1 hour" offer
 */
@Composable
fun AdFreeButton() {
    val ready by AdsManager.isReady.collectAsStateWithLifecycle()
    if (!ready) return
    val adFree by AdsManager.adFree.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            if (adFree) Icons.Rounded.Verified else Icons.Rounded.CardGiftcard,
            contentDescription = stringResource(R.string.adfree_content_description),
            modifier = if (adFree) Modifier else Modifier.pulse(0.92f, 1.08f, 1400)
        )
    }
    if (open) AdFreeDialog(onDismiss = { open = false })
}

@Composable
private fun AdFreeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adFree by AdsManager.adFree.collectAsStateWithLifecycle()
    var waitingForVideo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AdsManager.preloadRewarded() }

    // If the video wasn't ready on tap, keep trying for a few seconds
    LaunchedEffect(waitingForVideo) {
        if (!waitingForVideo || activity == null) return@LaunchedEffect
        repeat(16) {
            delay(500)
            if (AdsManager.showRewardedForAdFree(activity) { onGranted(context, onDismiss) }) {
                waitingForVideo = false
                return@LaunchedEffect
            }
        }
        waitingForVideo = false
        Toast.makeText(context, R.string.adfree_no_video, Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(Icons.Rounded.CardGiftcard, BrandGradients.splash, size = 64.dp, iconSize = 32.dp) },
        title = { Text(stringResource(if (adFree) R.string.adfree_title_active else R.string.adfree_title_offer), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (adFree) {
                        AdsManager.adFreeMinutesLeft().toInt().let { pluralStringResource(R.plurals.adfree_minutes_left, it, it) }
                    } else {
                        stringResource(R.string.adfree_offer_message)
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!adFree) {
                    Spacer(Modifier.height(20.dp))
                    if (waitingForVideo) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    } else {
                        GradientButton(
                            text = stringResource(R.string.adfree_watch_video),
                            icon = Icons.Rounded.PlayCircle,
                            brush = BrandGradients.splash,
                            onClick = {
                                if (activity == null) return@GradientButton
                                val shown = AdsManager.showRewardedForAdFree(activity) { onGranted(context, onDismiss) }
                                if (!shown) waitingForVideo = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (activity != null && ConsentManager.isPrivacyOptionsRequired(activity)) {
                    TextButton(onClick = { ConsentManager.showPrivacyOptions(activity) }) {
                        Text(stringResource(R.string.adfree_privacy_settings))
                    }
                }
                TextButton(onClick = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                    )
                }) {
                    Text(stringResource(R.string.settings_privacy_policy))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (adFree) R.string.adfree_great else R.string.adfree_not_now)) }
        }
    )
}

private fun onGranted(context: android.content.Context, onDismiss: () -> Unit) {
    Toast.makeText(context, R.string.adfree_granted, Toast.LENGTH_LONG).show()
    onDismiss()
}
