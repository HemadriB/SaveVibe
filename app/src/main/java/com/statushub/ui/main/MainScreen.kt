package com.statushub.ui.main

import com.statushub.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.statushub.utils.Engagement
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.statushub.data.StatusItem
import com.statushub.data.StatusSource
import com.statushub.data.StatusType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.statushub.ads.AdFreeButton
import com.statushub.ads.NativeAdCard
import com.statushub.ui.components.AnimatedSaveButton
import com.statushub.ui.components.GridEntry
import com.statushub.ui.components.withAdSlots
import com.statushub.ui.components.EmptyState
import com.statushub.ui.components.SaveState
import com.statushub.ui.components.animateEntrance
import com.statushub.ui.components.pressScale
import com.statushub.ui.components.shimmer
import com.statushub.ui.components.GradientButton
import com.statushub.ui.components.GradientHeader
import com.statushub.ui.components.IconBadge
import com.statushub.ui.theme.BrandGradients
import com.statushub.ui.settings.SettingsMenuButton
import com.statushub.utils.PermissionHelper
import com.statushub.utils.StatusScanner
import com.statushub.viewmodel.MainViewModel

/**
 * Main Screen with Images / Videos switcher
 * Displays viewed WhatsApp / WhatsApp Business statuses in a grid
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToPreview: (StatusItem) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Collect ViewModel state
    val images by viewModel.images.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()
    val sourcesNeedingAccess by viewModel.sourcesNeedingAccess.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val savingUris by viewModel.savingUris.collectAsStateWithLifecycle()
    val savedUris by viewModel.savedUris.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Android 10 and below: classic storage permission (empty list on Android 11+)
    val permissionsState = rememberMultiplePermissionsState(
        permissions = PermissionHelper.getStoragePermissions().toList()
    )
    val needsPermission = !StatusScanner.usesFolderPicker() && !permissionsState.allPermissionsGranted

    // Android 11+: system folder picker, opened directly at the .Statuses folder
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val source = viewModel.onFolderPicked(uri)
        Toast.makeText(
            context,
            if (source != null) context.getString(R.string.status_source_unlocked, source.label) else
                context.getString(R.string.status_wrong_folder),
            Toast.LENGTH_LONG
        ).show()
    }
    val requestAccess: (StatusSource) -> Unit = { source ->
        folderPicker.launch(StatusScanner.pickerInitialUri(source))
    }
    val openWhatsApp: () -> Unit = {
        StatusSource.entries.firstOrNull { StatusScanner.isInstalled(context, it) }?.let {
            context.packageManager.getLaunchIntentForPackage(it.packageName)?.let(context::startActivity)
        }
    }

    // Re-scan whenever the user comes back (e.g. after viewing statuses in WhatsApp)
    LifecycleResumeEffect(permissionsState.allPermissionsGranted) {
        if (!needsPermission) viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (needsPermission) permissionsState.launchMultiplePermissionRequest()
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.status_title),
                subtitle = if (hasAccess && !needsPermission) {
                    pluralStringResource(R.plurals.status_subtitle_count, images.size + videos.size, images.size + videos.size)
                } else {
                    stringResource(R.string.status_subtitle_default)
                },
                brush = BrandGradients.status,
                icon = Icons.Rounded.DonutLarge
            ) {
                AdFreeButton()
                IconButton(onClick = { viewModel.refresh(userInitiated = true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
                SettingsMenuButton()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                needsPermission -> EmptyState(
                    icon = Icons.Rounded.Lock,
                    brush = BrandGradients.status,
                    title = stringResource(R.string.status_storage_needed_title),
                    message = stringResource(R.string.status_storage_needed_message)
                ) {
                    GradientButton(
                        text = stringResource(R.string.status_grant_permission),
                        icon = Icons.Rounded.CheckCircle,
                        brush = BrandGradients.status,
                        onClick = { permissionsState.launchMultiplePermissionRequest() }
                    )
                }
                !hasAccess -> FolderAccessContent(
                    sources = sourcesNeedingAccess,
                    onRequestAccess = requestAccess
                )
                else -> {
                    MediaTypeSwitcher(
                        selected = selectedTab,
                        imageCount = images.size,
                        videoCount = videos.size,
                        onSelect = { selectedTab = it }
                    )

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh(userInitiated = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val itemsToShow = if (selectedTab == 0) images else videos
                        AnimatedContent(
                            targetState = Triple(isLoading, selectedTab, itemsToShow.isEmpty()),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "statusContent"
                        ) { (loading, tab, empty) ->
                            when {
                                loading -> SkeletonGrid()
                                empty -> EmptyState(
                                    icon = if (tab == 0) Icons.Rounded.Image else Icons.Rounded.Videocam,
                                    brush = BrandGradients.status,
                                    title = stringResource(if (tab == 0) R.string.status_empty_photos_title else R.string.status_empty_videos_title),
                                    message = stringResource(R.string.status_empty_message)
                                ) {
                                    GradientButton(
                                        text = stringResource(R.string.status_open_whatsapp),
                                        icon = Icons.Rounded.Visibility,
                                        brush = BrandGradients.status,
                                        onClick = openWhatsApp
                                    )
                                }
                                else -> StatusGrid(
                                    items = if (tab == 0) images else videos,
                                    sourcesNeedingAccess = sourcesNeedingAccess,
                                    onRequestAccess = requestAccess,
                                    onItemClick = onNavigateToPreview,
                                    saveStateOf = { item ->
                                        val key = item.uri.toString()
                                        when (key) {
                                            in savedUris -> SaveState.SAVED
                                            in savingUris -> SaveState.SAVING
                                            else -> SaveState.IDLE
                                        }
                                    },
                                    onSaveClick = { item ->
                                        viewModel.saveStatus(item) { saved ->
                                            if (saved) Engagement.onSaveCompleted(activity, Engagement.KIND_STATUS)
                                            Toast.makeText(
                                                context,
                                                context.getString(if (saved) R.string.status_saved_toast else R.string.status_save_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shimmering placeholder tiles shown while statuses load
 */
@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
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
}

/**
 * Pill-shaped Images / Videos switcher with counts
 */
@Composable
private fun MediaTypeSwitcher(
    selected: Int,
    imageCount: Int,
    videoCount: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        SwitcherOption(stringResource(R.string.label_photos), imageCount, Icons.Rounded.Image, selected == 0, Modifier.weight(1f)) { onSelect(0) }
        SwitcherOption(stringResource(R.string.label_videos), videoCount, Icons.Rounded.Videocam, selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun SwitcherOption(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, label = "switcherBg"
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "switcherContent"
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.status_switcher_label, label, count), style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * Grid layout for displaying status items
 */
@Composable
fun StatusGrid(
    items: List<StatusItem>,
    sourcesNeedingAccess: List<StatusSource>,
    onRequestAccess: (StatusSource) -> Unit,
    onItemClick: (StatusItem) -> Unit,
    saveStateOf: (StatusItem) -> SaveState,
    onSaveClick: (StatusItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // e.g. WhatsApp granted but WhatsApp Business not yet
        items(sourcesNeedingAccess, span = { GridItemSpan(maxLineSpan) }) { source ->
            AssistChip(
                onClick = { onRequestAccess(source) },
                label = { Text(stringResource(R.string.status_also_show_source, source.label)) },
                leadingIcon = { Icon(Icons.Rounded.AddCircleOutline, contentDescription = null) },
                shape = CircleShape
            )
        }
        val entries = withAdSlots(items, slotPrefix = "status")
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
                is GridEntry.Item -> StatusItemCard(
                    statusItem = entry.value,
                    saveState = saveStateOf(entry.value),
                    onClick = { onItemClick(entry.value) },
                    onSaveClick = { onSaveClick(entry.value) },
                    modifier = Modifier.animateEntrance(entry.index)
                )
                is GridEntry.Ad -> NativeAdCard(entry.slot, Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/**
 * Card for displaying a single status item with a quick-save button
 */
@Composable
fun StatusItemCard(
    statusItem: StatusItem,
    saveState: SaveState,
    onClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            model = statusItem.uri,
            contentDescription = statusItem.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Bottom scrim so the buttons stay readable on bright images
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
        )
        if (statusItem.type == StatusType.VIDEO) {
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
        if (statusItem.source == StatusSource.WHATSAPP_BUSINESS) {
            Text(
                stringResource(R.string.status_business_badge),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        AnimatedSaveButton(
            state = saveState,
            onClick = onSaveClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        )
    }
}

/**
 * Asks the user to grant the WhatsApp .Statuses folder via the system folder picker (Android 11+)
 */
@Composable
fun FolderAccessContent(
    sources: List<StatusSource>,
    onRequestAccess: (StatusSource) -> Unit
) {
    EmptyState(
        icon = Icons.Rounded.FolderOpen,
        brush = BrandGradients.status,
        title = stringResource(R.string.status_unlock_title),
        message = stringResource(R.string.status_unlock_message) + "\n\n" + stringResource(R.string.not_affiliated)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepRow(1, Icons.Rounded.Visibility, stringResource(R.string.status_step_view))
                StepRow(2, Icons.Rounded.TouchApp, stringResource(R.string.status_step_tap))
                StepRow(3, Icons.Rounded.CheckCircle, stringResource(R.string.status_step_allow))
            }
        }
        Spacer(Modifier.height(20.dp))
        sources.forEach { source ->
            GradientButton(
                text = stringResource(R.string.status_allow_source, source.label),
                icon = Icons.Rounded.FolderOpen,
                brush = BrandGradients.status,
                onClick = { onRequestAccess(source) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StepRow(number: Int, icon: ImageVector, text: String) {
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}
