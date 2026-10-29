package com.hemadri.statushub.ui.saved

import com.hemadri.statushub.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.hemadri.statushub.ads.AdFreeButton
import com.hemadri.statushub.ads.NativeAdCard
import com.hemadri.statushub.ui.components.GridEntry
import com.hemadri.statushub.ui.components.animateEntrance
import com.hemadri.statushub.ui.components.withAdSlots
import com.hemadri.statushub.ui.components.pressScale
import com.hemadri.statushub.ui.components.shimmer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hemadri.statushub.repository.SavedMedia
import com.hemadri.statushub.ui.components.EmptyState
import com.hemadri.statushub.ui.components.GradientHeader
import com.hemadri.statushub.ui.components.IconBadge
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetPurple
import com.hemadri.statushub.utils.MediaActions
import com.hemadri.statushub.viewmodel.SavedViewModel
import java.text.DateFormat
import java.util.Date

private enum class SavedFilter(@androidx.annotation.StringRes val label: Int, val icon: ImageVector) {
    ALL(R.string.label_all, Icons.Rounded.Collections),
    PHOTOS(R.string.label_photos, Icons.Rounded.Image),
    VIDEOS(R.string.label_videos, Icons.Rounded.Videocam)
}

/**
 * Saved tab - shows saved statuses and downloaded videos with open/share/repost/delete actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    viewModel: SavedViewModel = viewModel()
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SavedMedia?>(null) }
    var filter by rememberSaveable { mutableStateOf(SavedFilter.ALL) }

    // Reload every time the tab is opened so new saves/downloads show up
    LaunchedEffect(Unit) { viewModel.load() }

    val filtered = when (filter) {
        SavedFilter.ALL -> items
        SavedFilter.PHOTOS -> items.filter { !it.isVideo }
        SavedFilter.VIDEOS -> items.filter { it.isVideo }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.tab_saved),
                subtitle = pluralStringResource(R.plurals.saved_subtitle_count, items.size, items.size),
                brush = BrandGradients.saved,
                icon = Icons.Rounded.Collections
            ) {
                AdFreeButton()
                IconButton(onClick = { viewModel.load() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isLoading && items.isNotEmpty(),
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && items.isEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(12) {
                        Box(
                            Modifier
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(18.dp))
                                .shimmer()
                        )
                    }
                }
                items.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Collections,
                    brush = BrandGradients.saved,
                    title = stringResource(R.string.saved_empty_title),
                    message = stringResource(R.string.saved_empty_message)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            SavedFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(stringResource(option.label)) },
                                    leadingIcon = {
                                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SunsetPurple.copy(alpha = 0.15f),
                                        selectedLabelColor = SunsetPurple,
                                        selectedLeadingIconColor = SunsetPurple
                                    )
                                )
                            }
                        }
                    }
                    val entries = withAdSlots(filtered, slotPrefix = "saved")
                    items(
                        entries,
                        key = { entry ->
                            when (entry) {
                                is GridEntry.Item -> entry.value.uri.toString()
                                is GridEntry.Ad -> entry.slot
                            }
                        },
                        span = { entry -> if (entry is GridEntry.Ad) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
                    ) { entry ->
                        when (entry) {
                            is GridEntry.Item -> SavedItemCard(
                                item = entry.value,
                                onClick = { selected = entry.value },
                                modifier = Modifier
                                    .animateItem()
                                    .animateEntrance(entry.index)
                            )
                            is GridEntry.Ad -> NativeAdCard(entry.slot, Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(
                                R.string.saved_item_meta,
                                stringResource(if (item.isVideo) R.string.label_video else R.string.label_photo),
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateModified))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.action_open), BrandGradients.saved) {
                        MediaActions.open(context, item.uri, item.mimeType)
                        selected = null
                    }
                    SheetAction(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.action_whatsapp), BrandGradients.status) {
                        MediaActions.repostToWhatsApp(context, item.uri, item.mimeType)
                        selected = null
                    }
                    SheetAction(Icons.Rounded.Share, stringResource(R.string.action_share), BrandGradients.instagram) {
                        MediaActions.share(context, item.uri, item.mimeType)
                        selected = null
                    }
                    SheetAction(
                        Icons.Rounded.Delete,
                        stringResource(R.string.action_delete),
                        Brush.linearGradient(listOf(Color(0xFFF87171), Color(0xFFDC2626)))
                    ) {
                        viewModel.delete(item) { deleted ->
                            if (!deleted) {
                                Toast.makeText(context, R.string.saved_delete_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        selected = null
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, brush: Brush, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        IconBadge(icon = icon, brush = brush, size = 52.dp, iconSize = 24.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SavedItemCard(item: SavedMedia, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, 0.96f)
            .aspectRatio(0.8f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.label_video), tint = Color.White)
            }
        }
    }
}
