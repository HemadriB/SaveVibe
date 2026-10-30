package com.statushub.ui.instagram

import androidx.compose.ui.res.stringResource
import com.statushub.R
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.statushub.data.DownloadedItem
import com.statushub.instagram.InstagramMedia
import com.statushub.ui.components.GradientButton
import com.statushub.ui.components.pressScale
import com.statushub.ui.theme.BrandGradients
import com.statushub.utils.MediaActions

/**
 * Round profile picture with Instagram's story ring: gradient = new stories, grey = already seen
 */
@Composable
fun StoryAvatar(
    imageUrl: String?,
    size: Dp,
    seen: Boolean = false,
    showRing: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val ring: Brush = if (seen) {
        Brush.linearGradient(listOf(Color(0xFFC7CCD1), Color(0xFFC7CCD1)))
    } else {
        BrandGradients.instagram
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (showRing) Modifier.background(ring) else Modifier)
            .padding(if (showRing) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (showRing) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2)
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Avatar + username below, used in the stories row */
@Composable
fun StoryCircle(
    imageUrl: String?,
    label: String,
    seen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, 0.9f)
            .width(76.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        StoryAvatar(imageUrl = imageUrl, size = 68.dp, seen = seen)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

fun timeAgo(epochSeconds: Long): String =
    if (epochSeconds <= 0) "" else DateUtils.getRelativeTimeSpanString(
        epochSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

/**
 * Full-screen viewer for a story / highlight frame streamed from Instagram, with Save and,
 * once saved, WhatsApp / Share
 */
@Composable
fun StoryViewerDialog(
    media: InstagramMedia,
    title: String,
    subtitle: String,
    isSaving: Boolean,
    saved: DownloadedItem?,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (media.isVideo) {
                val player = remember(media.url) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(media.url))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(player) { onDispose { player.release() } }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 110.dp)
                )
            } else {
                AsyncImage(
                    model = media.url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ig_cd_close), tint = Color.White)
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (saved != null) {
                    GradientButton(
                        text = "WhatsApp",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        brush = BrandGradients.status,
                        onClick = { MediaActions.repostToWhatsApp(context, saved.uri, saved.mimeType) },
                        modifier = Modifier.weight(1f)
                    )
                    GradientButton(
                        text = stringResource(R.string.ig_share),
                        icon = Icons.Rounded.Share,
                        brush = BrandGradients.instagram,
                        onClick = { MediaActions.share(context, saved.uri, saved.mimeType) },
                        modifier = Modifier.weight(1f)
                    )
                } else if (isSaving) {
                    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else {
                    GradientButton(
                        text = stringResource(R.string.ig_save_to_gallery),
                        icon = Icons.Rounded.Download,
                        brush = BrandGradients.instagram,
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
