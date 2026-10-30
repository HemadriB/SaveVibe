package com.statushub.ui.home

import com.statushub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
import com.statushub.instagram.InstagramLinks
import com.statushub.ui.browser.BrowserScreen
import com.statushub.viewmodel.BrowserViewModel
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statushub.data.StatusItem
import com.statushub.ads.AdaptiveBanner
import com.statushub.ui.main.MainScreen
import com.statushub.ui.saved.SavedScreen
import com.statushub.ui.theme.SunsetPink
import com.statushub.ui.theme.SunsetPurple
import com.statushub.ui.theme.SunsetOrangeDeep
import com.statushub.ui.videodownload.VideoDownloadScreen
import com.statushub.viewmodel.MainViewModel
import com.statushub.viewmodel.SavedViewModel
import com.statushub.viewmodel.VideoDownloadViewModel

private enum class HomeTab(
    @androidx.annotation.StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val accent: Color
) {
    STATUS(R.string.tab_status, Icons.Rounded.DonutLarge, Icons.Outlined.DonutLarge, SunsetOrangeDeep),
    DOWNLOADER(R.string.tab_instagram, Icons.Rounded.SlowMotionVideo, Icons.Outlined.SlowMotionVideo, SunsetPink),
    BROWSER(R.string.tab_videos, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, Color(0xFFB4359E)),
    SAVED(R.string.tab_saved, Icons.Rounded.Collections, Icons.Outlined.Collections, SunsetPurple)
}

/**
 * Home screen - bottom navigation between Status saver, Instagram, any-site Videos and Saved files
 * with a banner ad above the navigation bar
 */
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onNavigateToPreview: (StatusItem) -> Unit,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    onOpenInstagramProfile: (com.statushub.instagram.InstagramUser) -> Unit = {},
    onOpenInstagramSearch: () -> Unit = {},
    downloadViewModel: VideoDownloadViewModel = viewModel(),
    savedViewModel: SavedViewModel = viewModel(),
    browserViewModel: BrowserViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(HomeTab.STATUS.ordinal) }

    // Link shared from Instagram: jump to the Downloader and start right away
    // Shared links: Instagram → Instagram tab, any other link → Videos tab
    LaunchedEffect(sharedText) {
        sharedText?.let { text ->
            val otherLink = Regex("https?://\\S+").find(text)?.value
            if (InstagramLinks.findInstagramUrl(text) != null || otherLink == null) {
                selectedTab = HomeTab.DOWNLOADER.ordinal
                downloadViewModel.startFromSharedText(text)
            } else {
                selectedTab = HomeTab.BROWSER.ordinal
                browserViewModel.open(otherLink)
            }
            onSharedTextConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                AdaptiveBanner()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    HomeTab.entries.forEach { tab ->
                        val selected = selectedTab == tab.ordinal
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab.ordinal },
                            icon = {
                                // Selected icon pops with a springy bounce
                                val scale by animateFloatAsState(
                                    if (selected) 1.18f else 1f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "navIconScale"
                                )
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                )
                            },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = tab.accent,
                                selectedTextColor = tab.accent,
                                indicatorColor = tab.accent.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            // Slide toward the tab you picked, with a fade
            AnimatedContent(
                targetState = HomeTab.entries[selectedTab],
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(tween(300)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(200)))
                },
                label = "homeTabs"
            ) { tab ->
                when (tab) {
                    HomeTab.STATUS -> MainScreen(
                        viewModel = mainViewModel,
                        onNavigateToPreview = onNavigateToPreview
                    )
                    HomeTab.DOWNLOADER -> VideoDownloadScreen(
                        viewModel = downloadViewModel,
                        onOpenProfile = onOpenInstagramProfile,
                        onOpenSearch = onOpenInstagramSearch
                    )
                    HomeTab.BROWSER -> BrowserScreen(
                        viewModel = browserViewModel,
                        onOpenInstagramTab = { selectedTab = HomeTab.DOWNLOADER.ordinal }
                    )
                    HomeTab.SAVED -> SavedScreen(viewModel = savedViewModel)
                }
            }
        }
    }
}
