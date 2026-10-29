package com.hemadri.statushub.ui.videodownload

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hemadri.statushub.R
import com.hemadri.statushub.utils.Engagement
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hemadri.statushub.ads.NativeAdCard
import com.hemadri.statushub.instagram.InstagramLoginActivity
import com.hemadri.statushub.instagram.InstagramUser
import com.hemadri.statushub.instagram.StoryTrayItem
import com.hemadri.statushub.ui.instagram.StoryCircle
import com.hemadri.statushub.ui.components.animateEntrance
import com.hemadri.statushub.ui.components.shimmer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import com.hemadri.statushub.ui.components.GradientButton
import com.hemadri.statushub.ui.components.GradientHeader
import com.hemadri.statushub.ui.components.IconBadge
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetPink
import com.hemadri.statushub.utils.MediaActions
import com.hemadri.statushub.viewmodel.DownloadState
import com.hemadri.statushub.viewmodel.VideoDownloadViewModel

/**
 * Downloader tab: Instagram posts, reels, carousels, stories (login) and direct video links
 */
@Composable
fun VideoDownloadScreen(
    viewModel: VideoDownloadViewModel = viewModel(),
    onOpenProfile: (InstagramUser) -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isInstagramLoggedIn.collectAsStateWithLifecycle()
    val storiesTray by viewModel.storiesTray.collectAsStateWithLifecycle()
    val isTrayLoading by viewModel.isTrayLoading.collectAsStateWithLifecycle()
    val trayError by viewModel.trayError.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, R.string.ig_logged_in_toast, Toast.LENGTH_SHORT).show()
            viewModel.onInstagramLoggedIn()
        }
    }
    val openLogin = { loginLauncher.launch(Intent(context, InstagramLoginActivity::class.java)) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.loadStoriesTray()
    }

    // Full-screen ad rules run only after a download has finished
    val downloadFinished = !downloadState.isBusy && downloadState.downloaded.isNotEmpty()
    LaunchedEffect(downloadFinished) {
        if (downloadFinished) Engagement.onSaveCompleted(context as? Activity, Engagement.KIND_INSTAGRAM)
    }

    // Auto-detect a copied Instagram link whenever the app regains focus
    // (Android 10+ only allows clipboard reads while the window is focused)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && viewModel.onClipboardText(clipboardManager.getText()?.text)) {
            Toast.makeText(context, R.string.ig_link_detected_toast, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            GradientHeader(
                title = stringResource(R.string.ig_header_title),
                subtitle = if (isLoggedIn) stringResource(R.string.ig_header_subtitle_logged_in) else stringResource(R.string.ig_header_subtitle),
                brush = BrandGradients.instagram,
                icon = Icons.Rounded.SlowMotionVideo
            ) {
                AssistChip(
                    onClick = { if (isLoggedIn) viewModel.logoutInstagram() else openLogin() },
                    label = { Text(if (isLoggedIn) stringResource(R.string.ig_logout) else stringResource(R.string.ig_login)) },
                    leadingIcon = {
                        Icon(
                            if (isLoggedIn) Icons.AutoMirrored.Rounded.Logout else Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = CircleShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        labelColor = Color.White,
                        leadingIconContentColor = Color.White
                    ),
                    border = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
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
            if (isLoggedIn) {
                StoriesSection(
                    tray = storiesTray,
                    isLoading = isTrayLoading,
                    error = trayError,
                    onOpenProfile = onOpenProfile,
                    onOpenSearch = onOpenSearch,
                    onRefresh = { viewModel.loadStoriesTray(force = true) }
                )
            } else {
                StoriesLoginPromo(onLogin = { openLogin() })
            }

            // URL Input Section
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
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.updateUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.ig_paste_link_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = stringResource(R.string.ig_cd_url), tint = SunsetPink) },
                        trailingIcon = {
                            // Clear the (auto-filled) link so the user can paste a different one
                            if (urlInput.isNotEmpty() && !downloadState.isBusy) {
                                IconButton(onClick = { viewModel.clearLink() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ig_cd_clear_link))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SunsetPink,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        enabled = !downloadState.isBusy
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { clipboardManager.getText()?.let { viewModel.updateUrl(it.text.trim()) } },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = CircleShape,
                            enabled = !downloadState.isBusy
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ig_paste))
                        }

                        GradientButton(
                            text = stringResource(R.string.ig_download),
                            icon = Icons.Rounded.Download,
                            brush = BrandGradients.instagram,
                            onClick = { viewModel.downloadVideo() },
                            modifier = Modifier.weight(1.4f),
                            enabled = !downloadState.isBusy && urlInput.isNotBlank()
                        )
                    }
                }
            }

            // One native ad card near the top, under the link box
            NativeAdCard(slot = "instagram_tab")

            AnimatedVisibility(
                visible = downloadState.isBusy,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ProgressCard(downloadState, onCancel = { viewModel.cancel() })
            }

            AnimatedVisibility(
                visible = downloadState.post != null || downloadState.downloaded.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + shrinkVertically()
            ) {
                ResultCard(
                    state = downloadState,
                    onCopyCaption = { caption ->
                        clipboardManager.setText(AnnotatedString(caption))
                        Toast.makeText(context, R.string.ig_caption_copied, Toast.LENGTH_SHORT).show()
                    },
                    onDownloadAnother = { viewModel.clearState() }
                )
            }

            downloadState.error?.let { error ->
                ErrorCard(
                    message = error,
                    loginSuggested = downloadState.loginSuggested,
                    onLogin = { openLogin() },
                    onDismiss = { viewModel.clearError() }
                )
            }

            HowToCard(isLoggedIn)
        }
    }
}

@Composable
private fun StoriesSection(
    tray: List<StoryTrayItem>,
    isLoading: Boolean,
    error: String?,
    onOpenProfile: (InstagramUser) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ig_stories), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ig_stories_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.ig_cd_refresh_stories))
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable(onClick = onOpenSearch)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = SunsetPink, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ig_search), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (isLoading && tray.isEmpty()) {
                    items(5) {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(68.dp)
                                .clip(CircleShape)
                                .shimmer()
                        )
                    }
                }
                itemsIndexed(tray, key = { _, it -> it.user.id.ifEmpty { it.user.username } }) { index, story ->
                    StoryCircle(
                        imageUrl = story.user.profilePicUrl,
                        label = if (story.isOwn) stringResource(R.string.ig_your_story) else story.user.username,
                        seen = story.seen,
                        onClick = { onOpenProfile(story.user) },
                        modifier = Modifier.animateEntrance(index)
                    )
                }
            }
            if (!isLoading && tray.isEmpty()) {
                Text(
                    error ?: stringResource(R.string.ig_no_new_stories),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StoriesLoginPromo(onLogin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            IconBadge(Icons.Rounded.AutoStories, BrandGradients.instagram, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ig_save_stories_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.ig_save_stories_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onLogin, shape = CircleShape) { Text(stringResource(R.string.ig_login)) }
        }
    }
}

@Composable
private fun ProgressCard(state: DownloadState, onCancel: () -> Unit) {
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
                IconBadge(Icons.Rounded.Download, BrandGradients.instagram, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isFetching -> stringResource(R.string.ig_fetching_post)
                            state.totalItems > 1 -> stringResource(R.string.ig_downloading_progress, state.currentItem, state.totalItems)
                            else -> stringResource(R.string.ig_downloading)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!state.isFetching) {
                        Text(
                            stringResource(R.string.ig_percent, state.progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.ig_cancel)) }
            }
            if (state.isFetching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = SunsetPink
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
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
}

@Composable
private fun ResultCard(
    state: DownloadState,
    onCopyCaption: (String) -> Unit,
    onDownloadAnother: () -> Unit
) {
    val context = LocalContext.current
    val post = state.post
    val finished = !state.isBusy && state.downloaded.isNotEmpty()
    val shareItems = state.downloaded.map { it.uri to it.mimeType }

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
            // Header: avatar initial + username + saved status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(BrandGradients.instagram),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (post?.username?.firstOrNull() ?: '✓').uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        post?.username?.let { "@$it" } ?: stringResource(R.string.ig_video),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (finished) {
                        Text(
                            if (state.downloaded.all { it.savedToGallery }) {
                                pluralStringResource(R.plurals.ig_saved_to_gallery_count, state.downloaded.size, state.downloaded.size)
                            } else {
                                stringResource(R.string.ig_saved_to_location, state.downloaded.first().location)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (finished) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.ig_cd_saved), tint = Color(0xFF22C55E))
                }
            }

            // Thumbnails: downloaded files when available, otherwise Instagram previews
            val thumbnails: List<Pair<Any?, Boolean>> = post?.media?.mapIndexed { i, media ->
                (state.downloaded.getOrNull(i)?.uri ?: media.thumbnailUrl) to media.isVideo
            } ?: state.downloaded.map { it.uri to it.isVideo }
            if (thumbnails.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(thumbnails) { index, (model, isVideo) ->
                        val item = state.downloaded.getOrNull(index)
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = item != null) {
                                    item?.let { MediaActions.open(context, it.uri, it.mimeType) }
                                }
                        ) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.ig_video), tint = Color.White)
                                }
                            }
                            if (item == null && state.isBusy) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }

            post?.caption?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (finished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionPill(Icons.AutoMirrored.Rounded.Send, "WhatsApp", Modifier.weight(1f)) {
                        MediaActions.repostToWhatsApp(context, shareItems)
                    }
                    ActionPill(Icons.Rounded.Share, stringResource(R.string.ig_share), Modifier.weight(1f)) {
                        MediaActions.share(context, shareItems)
                    }
                    post?.caption?.let { caption ->
                        ActionPill(Icons.Rounded.ContentCopy, stringResource(R.string.ig_caption), Modifier.weight(1f)) {
                            onCopyCaption(caption)
                        }
                    }
                }
                GradientButton(
                    text = stringResource(R.string.ig_download_another),
                    icon = Icons.Rounded.Link,
                    brush = BrandGradients.instagram,
                    onClick = onDownloadAnother,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = SunsetPink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ErrorCard(message: String, loginSuggested: Boolean, onLogin: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ig_couldnt_download),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loginSuggested) {
                    GradientButton(
                        text = stringResource(R.string.ig_login_to_instagram),
                        icon = Icons.AutoMirrored.Rounded.Login,
                        brush = BrandGradients.instagram,
                        onClick = onLogin
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ig_dismiss)) }
            }
        }
    }
}

@Composable
private fun HowToCard(isLoggedIn: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.ig_how_it_works), style = MaterialTheme.typography.titleMedium)
            HowToStep(Icons.Rounded.Link, stringResource(R.string.ig_howto_copy_title), stringResource(R.string.ig_howto_copy_text))
            HowToStep(Icons.Rounded.ContentPaste, stringResource(R.string.ig_howto_back_title), stringResource(R.string.ig_howto_back_text))
            HowToStep(Icons.Rounded.Download, stringResource(R.string.ig_download), stringResource(R.string.ig_howto_download_text))
            HowToStep(
                Icons.Rounded.Verified,
                stringResource(R.string.ig_howto_private_title),
                if (isLoggedIn) stringResource(R.string.ig_howto_private_logged_in) else stringResource(R.string.ig_howto_private_logged_out)
            )
            Text(
                stringResource(R.string.ig_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HowToStep(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, BrandGradients.instagram, size = 38.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
