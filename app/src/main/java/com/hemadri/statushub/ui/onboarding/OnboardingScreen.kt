package com.hemadri.statushub.ui.onboarding

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hemadri.statushub.R
import com.hemadri.statushub.analytics.Analytics
import com.hemadri.statushub.data.StatusSource
import com.hemadri.statushub.ui.components.floating
import com.hemadri.statushub.ui.components.pressScale
import com.hemadri.statushub.ui.components.pulse
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetPink
import com.hemadri.statushub.ui.theme.SunsetPurple
import com.hemadri.statushub.ui.theme.SunsetOrangeDeep
import com.hemadri.statushub.utils.PermissionHelper
import com.hemadri.statushub.utils.PreferencesHelper
import com.hemadri.statushub.utils.StatusScanner
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val brush: Brush,
    val accent: Color,
    val icon: ImageVector,
    val orbitIcons: List<ImageVector>
)

private val pages = listOf(
    OnboardingPage(
        title = R.string.onboarding_status_title,
        description = R.string.onboarding_status_description,
        brush = BrandGradients.status,
        accent = SunsetOrangeDeep,
        icon = Icons.Rounded.Download,
        orbitIcons = listOf(Icons.Rounded.Image, Icons.Rounded.Videocam, Icons.Rounded.Favorite)
    ),
    OnboardingPage(
        title = R.string.onboarding_reels_title,
        description = R.string.onboarding_reels_description,
        brush = BrandGradients.instagram,
        accent = SunsetPink,
        icon = Icons.Rounded.SlowMotionVideo,
        orbitIcons = listOf(Icons.Rounded.Link, Icons.Rounded.PlayArrow, Icons.Rounded.AutoStories)
    ),
    // Last page is the setup step (status access + alerts) rather than an illustration
    OnboardingPage(
        title = R.string.onboarding_setup_title,
        description = R.string.onboarding_setup_description,
        brush = BrandGradients.saved,
        accent = SunsetPurple,
        icon = Icons.Rounded.FolderOpen,
        orbitIcons = emptyList()
    )
)
private val setupPage = pages.lastIndex

/**
 * First-launch flow: two swipeable value pages, then a setup page that unlocks statuses
 * right away so the user lands on a filled grid instead of a permission screen.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction

    val installed = remember { StatusSource.entries.filter { StatusScanner.isInstalled(context, it) } }
    var needingAccess by remember { mutableStateOf(StatusScanner.sourcesNeedingAccess(context)) }
    val storagePermissions = rememberMultiplePermissionsState(PermissionHelper.getStoragePermissions().toList())
    val hasAccess = installed.isNotEmpty() && if (StatusScanner.usesFolderPicker()) {
        needingAccess.size < StatusScanner.installedSources(context).size
    } else {
        storagePermissions.allPermissionsGranted
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val source = StatusScanner.onFolderPicked(context, uri)
        if (source != null) {
            Analytics.log(Analytics.STATUS_ACCESS_GRANTED, "source" to source.name, "from" to "onboarding")
        } else {
            Analytics.log(Analytics.STATUS_ACCESS_WRONG_FOLDER, "from" to "onboarding")
            Toast.makeText(context, R.string.status_wrong_folder, Toast.LENGTH_LONG).show()
        }
        needingAccess = StatusScanner.sourcesNeedingAccess(context)
    }

    LifecycleResumeEffect(Unit) {
        needingAccess = StatusScanner.sourcesNeedingAccess(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { Analytics.log(Analytics.ONBOARDING_STARTED) }
    LaunchedEffect(pagerState.currentPage) {
        Analytics.log(Analytics.ONBOARDING_PAGE, "page" to pagerState.currentPage)
    }

    val finish = {
        Analytics.log(
            Analytics.ONBOARDING_COMPLETED,
            "access_granted" to hasAccess,
            "whatsapp_installed" to installed.isNotEmpty(),
            "notifications" to PreferencesHelper.isNewStatusAlertsEnabled(context)
        )
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background gradients blend into each other while swiping
        pages.forEachIndexed { index, page ->
            val alpha = (1f - (position - index).absoluteValue).coerceIn(0f, 1f)
            if (alpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha }
                        .background(page.brush)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                // Skip jumps to the setup step: it's the one that makes the app work
                if (pagerState.currentPage < setupPage) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(setupPage) } }) {
                        Text(stringResource(R.string.onboarding_skip), color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                val page = pages[index]
                val pageOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                if (index == setupPage) {
                    SetupPage(
                        page = page,
                        installed = installed,
                        needingAccess = needingAccess,
                        hasAccess = hasAccess,
                        onRequestFolder = { source -> folderPicker.launch(StatusScanner.pickerInitialUri(source)) },
                        onRequestStorage = { storagePermissions.launchMultiplePermissionRequest() },
                        modifier = Modifier.graphicsLayer { alpha = 1f - pageOffset.absoluteValue }
                    )
                    return@HorizontalPager
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                ) {
                    // Parallax: the illustration moves slower than the text
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = pageOffset * size.width * 0.35f
                            val scale = 1f - pageOffset.absoluteValue * 0.15f
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - pageOffset.absoluteValue * 0.6f
                        }
                    ) {
                        OnboardingIllustration(page)
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - pageOffset.absoluteValue
                            translationY = pageOffset.absoluteValue * 60f
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(page.description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - pageOffset.absoluteValue * 1.4f
                            translationY = pageOffset.absoluteValue * 90f
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    pages.indices.forEach { index ->
                        val width by animateDpAsState(if (pagerState.currentPage == index) 28.dp else 8.dp, label = "dot")
                        Box(
                            Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (pagerState.currentPage == index) 1f else 0.45f))
                        )
                    }
                }
                val isLast = pagerState.currentPage == setupPage
                val interaction = remember { MutableInteractionSource() }
                val label = when {
                    !isLast -> R.string.onboarding_next
                    hasAccess || installed.isEmpty() -> R.string.onboarding_start
                    else -> R.string.onboarding_later
                }
                Row(
                    modifier = Modifier
                        .pressScale(interaction)
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(if (isLast && !hasAccess && installed.isNotEmpty()) Color.White.copy(alpha = 0.25f) else Color.White)
                        .clickable(interactionSource = interaction, indication = null) {
                            if (isLast) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = if (isLast && !hasAccess && installed.isNotEmpty()) Color.White else pages[pagerState.currentPage].accent
                    AnimatedContent(
                        targetState = label,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ctaLabel"
                    ) { text ->
                        Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = tint)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tint)
                }
            }
        }
    }
}

/**
 * Setup step: shows what the system folder picker looks like, then opens it.
 * Also offers new-status alerts (Android 13+ asks for notification permission).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SetupPage(
    page: OnboardingPage,
    installed: List<StatusSource>,
    needingAccess: List<StatusSource>,
    hasAccess: Boolean,
    onRequestFolder: (StatusSource) -> Unit,
    onRequestStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(if (hasAccess) R.string.onboarding_setup_done_title else page.title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                when {
                    installed.isEmpty() -> R.string.onboarding_setup_no_whatsapp
                    hasAccess -> R.string.onboarding_setup_done_description
                    else -> page.description
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        if (installed.isNotEmpty()) {
            if (StatusScanner.usesFolderPicker()) {
                if (!hasAccess) {
                    FolderPickerPreview(installed.first())
                    Spacer(Modifier.height(16.dp))
                }
                installed.forEach { source ->
                    AccessButton(
                        text = stringResource(
                            if (source in needingAccess) R.string.onboarding_allow_source else R.string.onboarding_source_unlocked,
                            source.label
                        ),
                        done = source !in needingAccess,
                        accent = page.accent,
                        onClick = { onRequestFolder(source) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                AccessButton(
                    text = stringResource(if (hasAccess) R.string.onboarding_storage_granted else R.string.onboarding_allow_storage),
                    done = hasAccess,
                    accent = page.accent,
                    onClick = onRequestStorage
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        NotificationOptIn()

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.not_affiliated),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccessButton(text: String, done: Boolean, accent: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .then(if (done) Modifier else Modifier.pulse(0.98f, 1.02f, 1400))
            .height(54.dp)
            .clip(CircleShape)
            .background(if (done) Color.White.copy(alpha = 0.22f) else Color.White)
            .clickable(interactionSource = interaction, indication = null, enabled = !done, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = if (done) Color.White else accent
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (done) Color.White else accent)
    }
}

/**
 * A mock of the Android folder picker with the button to tap highlighted,
 * so the real picker feels familiar when it opens.
 */
@Composable
private fun FolderPickerPreview(source: StatusSource) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFF5F6368), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "… › ${source.scopedPath.substringAfterLast("/Media/").let { "Media › $it" }}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF3C4043),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8EAED))
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.onboarding_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5F6368),
                modifier = Modifier.weight(1f)
            )
            Box(contentAlignment = Alignment.BottomEnd) {
                Text(
                    stringResource(R.string.onboarding_picker_button).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .pulse(0.96f, 1.04f, 1200)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A73E8))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Icon(
                    Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = SunsetPink,
                    modifier = Modifier
                        .offset(x = 10.dp, y = 18.dp)
                        .floating(4.dp, 900)
                        .size(26.dp)
                )
            }
        }
    }
}

/** "Tell me about new statuses" switch; asks for notification permission on Android 13+ */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationOptIn() {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val permission = if (needsPermission) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted ->
            Analytics.log(Analytics.NOTIFICATIONS_PERMISSION, "granted" to granted, "from" to "onboarding")
            PreferencesHelper.setNewStatusAlertsEnabled(context, granted)
        }
    } else null
    var enabled by remember { mutableStateOf(PreferencesHelper.isNewStatusAlertsEnabled(context)) }
    val checked = enabled && (permission?.status?.isGranted ?: true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.onboarding_alerts),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { on ->
                enabled = on
                PreferencesHelper.setNewStatusAlertsEnabled(context, on)
                if (on && permission != null && !permission.status.isGranted) permission.launchPermissionRequest()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SunsetPurple,
                checkedTrackColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

/**
 * Rotating segmented "status ring" + pulsing halo + floating badge + orbiting mini icons
 */
@Composable
private fun OnboardingIllustration(page: OnboardingPage) {
    val transition = rememberInfiniteTransition(label = "illustration")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "ringRotation"
    )
    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(240.dp)
                .pulse(0.92f, 1.04f, 2200)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .rotate(rotation)
        ) {
            val segments = 6
            val gap = 14f
            val sweep = 360f / segments - gap
            repeat(segments) { i ->
                drawArc(
                    color = Color.White,
                    startAngle = i * (360f / segments),
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .floating(10.dp, 2000)
                .size(110.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(page.icon, contentDescription = null, tint = page.accent, modifier = Modifier.size(54.dp))
        }
        // Orbiting mini badges
        OrbitBadge(page.orbitIcons[0], page.accent, Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 10.dp), 1600)
        OrbitBadge(page.orbitIcons[1], page.accent, Modifier.align(Alignment.BottomStart).padding(bottom = 26.dp, start = 6.dp), 2100)
        OrbitBadge(page.orbitIcons[2], page.accent, Modifier.align(Alignment.CenterStart).padding(bottom = 120.dp), 2600, size = 40.dp)
    }
}

@Composable
private fun OrbitBadge(icon: ImageVector, tint: Color, modifier: Modifier, durationMs: Int, size: Dp = 52.dp) {
    Box(
        modifier = modifier
            .floating(12.dp, durationMs)
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
