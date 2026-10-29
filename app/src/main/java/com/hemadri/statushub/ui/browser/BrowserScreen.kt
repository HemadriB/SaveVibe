package com.hemadri.statushub.ui.browser

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hemadri.statushub.R
import com.hemadri.statushub.utils.Engagement
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hemadri.statushub.ads.NativeAdCard
import com.hemadri.statushub.browser.DetectedVideo
import com.hemadri.statushub.browser.VideoDetector
import com.hemadri.statushub.instagram.InstagramClient
import com.hemadri.statushub.ui.components.GradientButton
import com.hemadri.statushub.ui.components.IconBadge
import com.hemadri.statushub.ui.components.animateEntrance
import com.hemadri.statushub.ui.components.pressScale
import com.hemadri.statushub.ui.components.pulse
import com.hemadri.statushub.ui.theme.BrandGradients
import com.hemadri.statushub.ui.theme.SunsetOrange
import com.hemadri.statushub.ui.theme.SunsetOrangeDeep
import com.hemadri.statushub.ui.theme.SunsetPink
import com.hemadri.statushub.ui.theme.SunsetPurple
import com.hemadri.statushub.utils.MediaActions
import com.hemadri.statushub.viewmodel.BrowserDownload
import com.hemadri.statushub.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay

private data class SiteShortcut(val name: String, val url: String, val colors: List<Color>)

private val shortcuts = listOf(
    SiteShortcut("Facebook", "https://m.facebook.com/watch/", listOf(Color(0xFF4F7BF7), SunsetPurple)),
    SiteShortcut("X", "https://x.com/explore", listOf(Color(0xFF2B2B2B), Color(0xFF5A5A5A))),
    SiteShortcut("TikTok", "https://www.tiktok.com/", listOf(SunsetPink, Color(0xFF25C7D9))),
    SiteShortcut("Vimeo", "https://vimeo.com/watch", listOf(Color(0xFF1AB7EA), Color(0xFF2F80ED))),
    SiteShortcut("Dailymotion", "https://www.dailymotion.com/", listOf(Color(0xFF2F6BED), SunsetPurple)),
    SiteShortcut("Pinterest", "https://www.pinterest.com/", listOf(Color(0xFFE60023), SunsetPink)),
    SiteShortcut("Reddit", "https://www.reddit.com/r/videos/", listOf(SunsetOrangeDeep, SunsetOrange)),
    SiteShortcut("ShareChat", "https://sharechat.com/", listOf(Color(0xFFFF6A3D), SunsetPink)),
    SiteShortcut("Moj", "https://mojapp.in/", listOf(Color(0xFFFFB627), SunsetOrangeDeep)),
    SiteShortcut("Instagram", "instagram", listOf(SunsetOrange, SunsetPink, SunsetPurple))
)

/**
 * Videos tab: an in-app browser that finds downloadable videos on almost any site
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel(),
    onOpenInstagramTab: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val address by viewModel.addressText.collectAsStateWithLifecycle()
    val pageProgress by viewModel.pageProgress.collectAsStateWithLifecycle()
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val loadRequest by viewModel.loadRequest.collectAsStateWithLifecycle()
    val blockedMessage by viewModel.blockedMessage.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(blockedMessage) {
        blockedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearBlockedMessage()
        }
    }

    // Full-screen ad rules run only after a download has finished
    val finishedCount = downloads.values.count { it.result != null }
    var lastFinished by remember { mutableIntStateOf(finishedCount) }
    LaunchedEffect(finishedCount) {
        if (finishedCount > lastFinished) Engagement.onSaveCompleted(context as? Activity, Engagement.KIND_WEB_VIDEO)
        lastFinished = finishedCount
    }

    // Typed address / shortcut / shared link
    var handledRequest by remember { mutableIntStateOf(loadRequest) }
    LaunchedEffect(loadRequest, webView) {
        val view = webView ?: return@LaunchedEffect
        if (loadRequest != handledRequest) {
            handledRequest = loadRequest
            currentUrl?.let { view.loadUrl(it) }
        }
    }

    // Single-page sites (Facebook, TikTok, X) load videos without page reloads: re-scan periodically
    LaunchedEffect(webView, currentUrl) {
        val view = webView ?: return@LaunchedEffect
        while (true) {
            delay(3000)
            val pageUrl = view.url ?: continue
            view.evaluateJavascript(VideoDetector.PAGE_SCAN_SCRIPT) { viewModel.onPageScan(it, pageUrl) }
        }
    }

    BackHandler(enabled = currentUrl != null) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else viewModel.goHome()
    }

    Scaffold(
        topBar = {
            AddressBar(
                address = address,
                isPageOpen = currentUrl != null,
                progress = pageProgress,
                onAddressChange = viewModel::updateAddress,
                onGo = { viewModel.open(it) },
                onHome = { viewModel.goHome() },
                onReload = { webView?.reload() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentUrl == null) {
                BrowserHome(
                    onOpen = { shortcut ->
                        if (shortcut.url == "instagram") onOpenInstagramTab() else viewModel.open(shortcut.url)
                    },
                    onPaste = { viewModel.open(it) }
                )
            } else {
                BrowserWebView(
                    viewModel = viewModel,
                    initialUrl = currentUrl!!,
                    onCreated = { webView = it },
                    onReleased = { webView = null }
                )
            }

            AnimatedVisibility(
                visible = currentUrl != null && detected.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                FoundVideosButton(count = detected.size, onClick = { showSheet = true })
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            DetectedVideosSheet(
                videos = detected,
                downloads = downloads,
                onDownload = viewModel::download,
                onCancel = viewModel::cancel
            )
        }
    }
}

@Composable
private fun AddressBar(
    address: String,
    isPageOpen: Boolean,
    progress: Int,
    onAddressChange: (String) -> Unit,
    onGo: (String) -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(BrandGradients.splash)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
        ) {
            IconButton(onClick = onHome) {
                Icon(
                    if (isPageOpen) Icons.Rounded.Home else Icons.Rounded.Language,
                    contentDescription = stringResource(R.string.browser_home),
                    tint = Color.White
                )
            }
            TextField(
                value = address,
                onValueChange = onAddressChange,
                placeholder = { Text(stringResource(R.string.browser_address_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (address.isNotEmpty()) {
                        IconButton(onClick = { onAddressChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.browser_clear))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    onGo(address)
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            )
            if (isPageOpen) {
                IconButton(onClick = onReload) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.browser_reload), tint = Color.White)
                }
            }
        }
        if (isPageOpen && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {}
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    viewModel: BrowserViewModel,
    initialUrl: String,
    onCreated: (WebView) -> Unit,
    onReleased: () -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // WRAP_CONTENT (the AndroidView default) gives pages a 0px viewport height
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = InstagramClient.browserUserAgent(settings.userAgentString)
                viewModel.userAgent = settings.userAgentString
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        viewModel.onProgress(newProgress)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        return when {
                            VideoDetector.isBlocked(url) -> {
                                viewModel.onBlockedNavigation()
                                true
                            }
                            // App links (fb://, intent://, market://…) would leave the app
                            !url.startsWith("http") -> true
                            else -> false
                        }
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        viewModel.onPageStarted(url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        viewModel.onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(VideoDetector.PAGE_SCAN_SCRIPT) { viewModel.onPageScan(it, url) }
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        // Observe only: every media request the page makes is a download candidate
                        viewModel.onCandidate(request.url.toString(), viewModel.currentUrl.value ?: "")
                        return null
                    }
                }
                setDownloadListener { url, _, _, mimeType, _ ->
                    if (mimeType?.startsWith("video") == true) {
                        viewModel.onCandidate(url, viewModel.currentUrl.value ?: url)
                    }
                }
                loadUrl(initialUrl)
                onCreated(this)
            }
        },
        onRelease = { view ->
            onReleased()
            view.stopLoading()
            view.destroy()
        }
    )
}

@Composable
private fun FoundVideosButton(count: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressScale(interaction)
            .pulse(0.97f, 1.04f, 1200)
            .height(56.dp)
            .clip(CircleShape)
            .background(BrandGradients.splash)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = count, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "videoCount") { n ->
            Text(
                pluralStringResource(R.plurals.browser_video_count, n, n),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DetectedVideosSheet(
    videos: List<DetectedVideo>,
    downloads: Map<String, BrowserDownload>,
    onDownload: (DetectedVideo) -> Unit,
    onCancel: (DetectedVideo) -> Unit
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.browser_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            stringResource(R.string.browser_sheet_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            items(videos, key = { it.url }) { video ->
                DetectedVideoRow(
                    video = video,
                    download = downloads[video.url] ?: BrowserDownload(),
                    onDownload = { onDownload(video) },
                    onCancel = { onCancel(video) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun DetectedVideoRow(
    video: DetectedVideo,
    download: BrowserDownload,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandGradients.splash),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White)
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.pageTitle ?: stringResource(R.string.browser_video_untitled),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(if (video.kind == DetectedVideo.Kind.HLS) stringResource(R.string.browser_hd_stream) else "MP4")
                    video.quality?.let { InfoChip(it) }
                    video.sizeBytes?.let { InfoChip(formatSize(it)) }
                }
                download.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = when {
                    download.result != null -> 2
                    download.isRunning -> 1
                    else -> 0
                },
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "browserDownload"
            ) { state ->
                when (state) {
                    2 -> Row {
                        val saved = download.result!!
                        IconButton(onClick = { MediaActions.open(context, saved.uri, saved.mimeType) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.browser_play), tint = SunsetPink)
                        }
                        IconButton(onClick = { MediaActions.repostToWhatsApp(context, saved.uri, saved.mimeType) }) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "WhatsApp", tint = SunsetPink)
                        }
                        IconButton(onClick = { MediaActions.share(context, saved.uri, saved.mimeType) }) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.browser_share), tint = SunsetPink)
                        }
                    }
                    1 -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onCancel)
                    ) {
                        CircularProgressIndicator(
                            progress = { download.progress / 100f },
                            color = SunsetPink,
                            modifier = Modifier.size(40.dp)
                        )
                        Text("${download.progress}", style = MaterialTheme.typography.labelSmall)
                    }
                    else -> Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDownload)
                    ) {
                        IconBadge(Icons.Rounded.Download, BrandGradients.splash, size = 44.dp, iconSize = 24.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = SunsetPink,
        modifier = Modifier
            .clip(CircleShape)
            .background(SunsetPink.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    else -> "${bytes / 1000} KB"
}

@Composable
private fun BrowserHome(onOpen: (SiteShortcut) -> Unit, onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.animateEntrance(0)) {
            Text(stringResource(R.string.browser_home_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.browser_home_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GradientButton(
            text = stringResource(R.string.browser_paste_and_open),
            icon = Icons.Rounded.ContentPaste,
            brush = BrandGradients.splash,
            onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { onPaste(it.trim()) } },
            modifier = Modifier
                .fillMaxWidth()
                .animateEntrance(1)
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.animateEntrance(2)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.browser_popular_sites), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                shortcuts.chunked(5).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, site ->
                            SiteButton(site, Modifier.animateEntrance(3 + rowIndex * 5 + i)) { onOpen(site) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        NativeAdCard(slot = "browser_home")

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.animateEntrance(4)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.browser_how_it_works), style = MaterialTheme.typography.titleMedium)
                HomeStep(Icons.Rounded.Language, stringResource(R.string.browser_step_open))
                HomeStep(Icons.Rounded.TouchApp, stringResource(R.string.browser_step_play))
                HomeStep(Icons.Rounded.Download, stringResource(R.string.browser_step_save))
                Text(
                    stringResource(R.string.browser_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SiteButton(site: SiteShortcut, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, 0.88f)
            .width(60.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(site.colors)),
            contentAlignment = Alignment.Center
        ) {
            Text(site.name.take(1), style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            site.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeStep(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, BrandGradients.splash, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
