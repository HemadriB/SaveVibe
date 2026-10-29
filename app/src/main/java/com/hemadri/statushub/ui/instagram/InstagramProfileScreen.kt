package com.hemadri.statushub.ui.instagram

import androidx.compose.ui.res.stringResource
import com.hemadri.statushub.R
import com.hemadri.statushub.utils.Engagement
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.hemadri.statushub.ads.AdaptiveBanner
import com.hemadri.statushub.ads.NativeAdCard
import com.hemadri.statushub.ui.components.AnimatedSaveButton
import com.hemadri.statushub.ui.components.GridEntry
import com.hemadri.statushub.ui.components.withAdSlots
import com.hemadri.statushub.ui.components.SaveState
import com.hemadri.statushub.ui.components.animateEntrance
import com.hemadri.statushub.ui.components.pressScale
import com.hemadri.statushub.ui.components.shimmer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hemadri.statushub.instagram.Highlight
import com.hemadri.statushub.instagram.StoryItem
import com.hemadri.statushub.ui.components.EmptyState
import com.hemadri.statushub.ui.components.GradientButton
import com.hemadri.statushub.ui.components.IconBadge
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetPink
import com.hemadri.statushub.viewmodel.InstagramProfileViewModel

/**
 * Instagram profile: current stories, highlights and HD profile picture, all downloadable
 */
@Composable
fun InstagramProfileScreen(
    onBack: () -> Unit,
    viewModel: InstagramProfileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<StoryItem?>(null) }

    LaunchedEffect(state.error) {
        state.error?.takeIf { state.user != null }?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Full-screen ad rules run after each finished save (never before)
    val activity = context as? android.app.Activity
    var savedCount by remember { mutableIntStateOf(state.saved.size) }
    LaunchedEffect(state.saved.size) {
        if (state.saved.size > savedCount) Engagement.onSaveCompleted(activity, Engagement.KIND_INSTAGRAM_PROFILE)
        savedCount = state.saved.size
    }

    Scaffold(
        bottomBar = { AdaptiveBanner(withNavigationBarPadding = true) },
        topBar = {
            ProfileHeader(
                username = state.username,
                fullName = state.user?.fullName,
                pictureUrl = state.user?.profilePicUrl,
                hasStories = state.selectedHighlight == null && state.items.isNotEmpty(),
                pictureSaved = InstagramProfileViewModel.PROFILE_PICTURE_ID in state.saved,
                pictureSaving = InstagramProfileViewModel.PROFILE_PICTURE_ID in state.saving,
                onSavePicture = { viewModel.saveProfilePicture() },
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val user = state.user
            when {
                state.isLoadingProfile -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = SunsetPink)
                user == null -> EmptyState(
                    icon = Icons.Rounded.AccountCircle,
                    brush = BrandGradients.instagram,
                    title = stringResource(R.string.ig_profile_error_title, state.username),
                    message = state.error ?: stringResource(R.string.ig_try_again_message)
                ) {
                    GradientButton(stringResource(R.string.ig_try_again), null, BrandGradients.instagram, onClick = { viewModel.loadProfile() })
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HighlightsRow(
                            storiesPictureUrl = user.profilePicUrl,
                            highlights = state.highlights,
                            isLoadingHighlights = state.isLoadingHighlights,
                            selected = state.selectedHighlight,
                            onSelect = { viewModel.loadItems(it) },
                            onLoadHighlights = { viewModel.loadHighlights() }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                state.selectedHighlight?.title?.ifBlank { stringResource(R.string.ig_highlight) } ?: stringResource(R.string.ig_current_stories),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            val unsaved = state.items.count { it.id !in state.saved }
                            if (state.items.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = { viewModel.saveAll() },
                                    enabled = unsaved > 0,
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        if (unsaved > 0) Icons.Rounded.Download else Icons.Rounded.DownloadDone,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (unsaved > 0) stringResource(R.string.ig_save_all, unsaved) else stringResource(R.string.ig_all_saved))
                                }
                            }
                        }
                    }
                    when {
                        state.isLoadingItems -> items(6) {
                            Box(
                                Modifier
                                    .aspectRatio(9f / 16f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .shimmer()
                            )
                        }
                        state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = if (user.isPrivate) Icons.Rounded.Lock else Icons.Rounded.HourglassEmpty,
                                brush = BrandGradients.instagram,
                                title = when {
                                    state.error != null -> stringResource(R.string.ig_couldnt_load)
                                    user.isPrivate -> stringResource(R.string.ig_private_account)
                                    else -> stringResource(R.string.ig_no_active_stories)
                                },
                                message = when {
                                    state.error != null -> state.error!!
                                    user.isPrivate -> stringResource(R.string.ig_private_account_message)
                                    else -> stringResource(R.string.ig_no_active_stories_message)
                                },
                                modifier = Modifier.height(320.dp)
                            )
                        }
                        else -> items(
                            withAdSlots(state.items, slotPrefix = "stories_${state.username}"),
                            key = { entry ->
                                when (entry) {
                                    is GridEntry.Item -> entry.value.id
                                    is GridEntry.Ad -> entry.slot
                                }
                            },
                            span = { entry -> if (entry is GridEntry.Ad) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                        ) { entry ->
                            when (entry) {
                                is GridEntry.Item -> StoryTile(
                                    item = entry.value,
                                    isSaving = entry.value.id in state.saving,
                                    isSaved = entry.value.id in state.saved,
                                    onClick = { viewing = entry.value },
                                    onSave = { viewModel.save(entry.value) },
                                    modifier = Modifier.animateEntrance(entry.index)
                                )
                                is GridEntry.Ad -> NativeAdCard(entry.slot, Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { item ->
        StoryViewerDialog(
            media = item.media,
            title = "@${state.username}",
            subtitle = timeAgo(item.takenAt),
            isSaving = item.id in state.saving,
            saved = state.saved[item.id],
            onSave = { viewModel.save(item) },
            onDismiss = { viewing = null }
        )
    }
}

@Composable
private fun ProfileHeader(
    username: String,
    fullName: String?,
    pictureUrl: String?,
    hasStories: Boolean,
    pictureSaved: Boolean,
    pictureSaving: Boolean,
    onSavePicture: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(BrandGradients.instagram)
            .statusBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ig_cd_back), tint = Color.White)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(3.dp, Color.White.copy(alpha = if (hasStories) 1f else 0.4f), CircleShape)
                    .padding(4.dp)
            ) {
                StoryAvatar(imageUrl = pictureUrl, size = 92.dp, showRing = false)
            }
            Spacer(Modifier.height(10.dp))
            Text("@$username", style = MaterialTheme.typography.titleLarge, color = Color.White)
            fullName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            AssistChip(
                onClick = onSavePicture,
                enabled = pictureUrl != null && !pictureSaved && !pictureSaving,
                label = {
                    Text(
                        when {
                            pictureSaved -> stringResource(R.string.ig_profile_photo_saved)
                            pictureSaving -> stringResource(R.string.ig_saving)
                            else -> stringResource(R.string.ig_save_hd_profile_photo)
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        if (pictureSaved) Icons.Rounded.Check else Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    labelColor = Color.White,
                    leadingIconContentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledLabelColor = Color.White.copy(alpha = 0.8f),
                    disabledLeadingIconContentColor = Color.White.copy(alpha = 0.8f)
                ),
                border = null
            )
        }
    }
}

@Composable
private fun HighlightsRow(
    storiesPictureUrl: String?,
    highlights: List<Highlight>?,
    isLoadingHighlights: Boolean,
    selected: Highlight?,
    onSelect: (Highlight?) -> Unit,
    onLoadHighlights: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        item {
            HighlightCircle(stringResource(R.string.ig_stories), storiesPictureUrl, selected == null, isStories = true) { onSelect(null) }
        }
        if (highlights == null) {
            // Loaded on demand: one less Instagram request every time a profile is opened
            item {
                HighlightCircle(
                    if (isLoadingHighlights) stringResource(R.string.ig_loading) else stringResource(R.string.ig_highlights),
                    null,
                    selected = false,
                    isStories = true,
                    icon = Icons.Rounded.Collections,
                    onClick = onLoadHighlights
                )
            }
        } else if (highlights.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.ig_no_highlights),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        items(highlights.orEmpty(), key = { it.id }) { highlight ->
            HighlightCircle(highlight.title, highlight.coverUrl, selected?.id == highlight.id) { onSelect(highlight) }
        }
    }
}

@Composable
private fun HighlightCircle(
    title: String,
    coverUrl: String?,
    selected: Boolean,
    isStories: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.AutoStories,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (selected) BrandGradients.instagram else Brush.linearGradient(listOf(Color(0xFFD5DADF), Color(0xFFD5DADF))))
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isStories) {
                Icon(icon, contentDescription = null, tint = SunsetPink)
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title.ifBlank { stringResource(R.string.ig_highlight) },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) SunsetPink else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoryTile(
    item: StoryItem,
    isSaving: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.96f)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = item.media.thumbnailUrl ?: item.media.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
        )
        if (item.media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.ig_video), tint = Color.White)
            }
        }
        timeAgo(item.takenAt).takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
        AnimatedSaveButton(
            state = when {
                isSaved -> SaveState.SAVED
                isSaving -> SaveState.SAVING
                else -> SaveState.IDLE
            },
            onClick = onSave,
            brush = BrandGradients.instagram,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        )
    }
}
