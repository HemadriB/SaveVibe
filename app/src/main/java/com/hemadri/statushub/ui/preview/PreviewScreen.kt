package com.hemadri.statushub.ui.preview

import com.hemadri.statushub.R
import androidx.compose.ui.res.stringResource
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.hemadri.statushub.data.StatusItem
import com.hemadri.statushub.data.StatusType
import com.hemadri.statushub.ui.components.GradientButton
import com.hemadri.statushub.ui.components.pressScale
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.utils.MediaActions
import kotlin.math.absoluteValue

/**
 * Full-screen, swipeable preview of statuses (photos or videos) with Repost / Share / Save.
 */
@Composable
fun PreviewScreen(
    items: List<StatusItem>,
    initialIndex: Int,
    savingUris: Set<String>,
    savedUris: Set<String>,
    onSave: (StatusItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    // One player for the whole pager: it follows the page you settle on
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(pagerState.settledPage) {
        val item = items[pagerState.settledPage]
        if (item.type == StatusType.VIDEO) {
            player.setMediaItem(MediaItem.fromUri(item.uri))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { items[it].uri.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Depth effect: neighbours shrink and dim while swiping
                    .graphicsLayer {
                        val scale = 1f - (offset * 0.12f).coerceAtMost(0.12f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (offset * 0.5f).coerceAtMost(0.5f)
                    }
            ) {
                if (item.type == StatusType.VIDEO && page == pagerState.settledPage) {
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
                            .padding(bottom = 130.dp)
                    )
                } else {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (item.type == StatusType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }

        // Top: back, source, counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(current.source.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    stringResource(R.string.preview_swipe_for_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.preview_position, pagerState.currentPage + 1, items.size),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom: action bar for the current page
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundAction(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.action_whatsapp)) {
                MediaActions.repostToWhatsApp(context, current.uri, current.mimeType)
            }
            Spacer(Modifier.width(12.dp))
            RoundAction(Icons.Rounded.Share, stringResource(R.string.action_share)) {
                MediaActions.share(context, current.uri, current.mimeType)
            }
            Spacer(Modifier.width(16.dp))
            val key = current.uri.toString()
            val state = when (key) {
                in savedUris -> 2
                in savingUris -> 1
                else -> 0
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "previewSave"
                ) { s ->
                    when (s) {
                        2 -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.label_saved), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        1 -> Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                        }
                        else -> GradientButton(
                            text = stringResource(R.string.preview_save_to_gallery),
                            icon = Icons.Rounded.Download,
                            brush = BrandGradients.status,
                            onClick = { onSave(current) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            interactionSource = interaction,
            modifier = Modifier
                .pressScale(interaction, 0.88f)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
