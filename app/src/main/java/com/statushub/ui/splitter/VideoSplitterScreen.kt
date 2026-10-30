package com.statushub.ui.splitter

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.statushub.R
import com.statushub.ui.components.GradientButton
import com.statushub.ui.components.GradientHeader
import com.statushub.ui.components.IconBadge
import com.statushub.ui.theme.BrandGradients
import com.statushub.ui.theme.SunsetOrange
import com.statushub.ui.theme.SunsetPink
import com.statushub.utils.MediaActions
import com.statushub.viewmodel.ClipItem
import com.statushub.viewmodel.VideoSplitterViewModel

/**
 * Video Splitter screen: pick a video, choose clip length, split into
 * 30-second clips ready for WhatsApp status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSplitterScreen(
    viewModel: VideoSplitterViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setVideo(it) }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.splitter_title),
                subtitle = stringResource(R.string.splitter_subtitle),
                brush = BrandGradients.status,
                icon = Icons.Rounded.ContentCut
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pick video card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.VideoFile, BrandGradients.status, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.splitter_pick_video),
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (state.durationMs > 0) {
                                Text(
                                    stringResource(R.string.splitter_duration, formatDuration(state.durationMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    GradientButton(
                        text = if (state.videoUri == null) stringResource(R.string.splitter_choose_video)
                        else stringResource(R.string.splitter_change_video),
                        icon = Icons.Rounded.Movie,
                        brush = BrandGradients.status,
                        onClick = { videoPicker.launch("video/*") },
                        enabled = !state.isSplitting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Duration selector
            AnimatedVisibility(
                visible = state.videoUri != null && state.durationMs > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = SunsetOrange)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.splitter_clip_length),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // Quick-select chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(15, 30, 45, 60).forEach { seconds ->
                                FilterChip(
                                    selected = state.clipDurationS == seconds,
                                    onClick = { viewModel.setClipDuration(seconds) },
                                    label = { Text("${seconds}s") },
                                    shape = CircleShape,
                                    enabled = !state.isSplitting,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SunsetPink.copy(alpha = 0.15f),
                                        selectedLabelColor = SunsetPink
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Clip count info
                        Text(
                            stringResource(
                                R.string.splitter_will_create,
                                state.totalClips,
                                state.clipDurationS
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Split button
                        GradientButton(
                            text = stringResource(R.string.splitter_split_now),
                            icon = Icons.Rounded.ContentCut,
                            brush = BrandGradients.status,
                            onClick = { viewModel.split() },
                            enabled = !state.isSplitting,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Progress
            AnimatedVisibility(
                visible = state.isSplitting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.ContentCut, BrandGradients.status, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.splitter_splitting),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.splitter_progress, state.progress, state.totalClips),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalClips > 0) state.progress.toFloat() / state.totalClips
                                else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = SunsetPink,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            // Results
            AnimatedVisibility(
                visible = state.result != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.result?.let { clips ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.splitter_done, clips.size),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Clip thumbnails
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(clips) { index, clip ->
                                    ClipCard(
                                        clip = clip,
                                        index = index,
                                        onPlay = {
                                            MediaActions.open(context, MediaActions.uriForFile(context, clip.file), "video/mp4")
                                        },
                                        onSave = { viewModel.saveClipToGallery(clip) },
                                        onShare = {
                                            MediaActions.share(
                                                context,
                                                MediaActions.uriForFile(context, clip.file),
                                                "video/mp4"
                                            )
                                        }
                                    )
                                }
                            }

                            // Save all + split another
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val allSaved = clips.all { it.saved }
                                GradientButton(
                                    text = if (allSaved) stringResource(R.string.splitter_all_saved)
                                    else stringResource(R.string.splitter_save_all),
                                    icon = if (allSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                    brush = if (allSaved) Brush.linearGradient(
                                        listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                                    ) else BrandGradients.status,
                                    onClick = {
                                        if (!allSaved) {
                                            viewModel.saveAllClips()
                                            Toast.makeText(context, R.string.splitter_saving_all, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !allSaved,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.reset()
                                    videoPicker.launch("video/*")
                                },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.splitter_split_another))
                            }
                        }
                    }
                }
            }

            // Error
            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // How it works
            if (state.result == null && !state.isSplitting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.splitter_how_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        HowStep(1, stringResource(R.string.splitter_how_step1))
                        HowStep(2, stringResource(R.string.splitter_how_step2))
                        HowStep(3, stringResource(R.string.splitter_how_step3))
                        HowStep(4, stringResource(R.string.splitter_how_step4))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(
    clip: ClipItem,
    index: Int,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = clip.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    "Part ${index + 1}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (clip.saved) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Saved", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null,
                                    tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                            },
                            shape = CircleShape,
                            modifier = Modifier.height(28.dp)
                        )
                    } else {
                        IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
