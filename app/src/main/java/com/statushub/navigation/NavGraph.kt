package com.statushub.navigation

import com.statushub.utils.Engagement
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.statushub.ui.onboarding.OnboardingScreen
import com.statushub.utils.PreferencesHelper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.statushub.ui.home.HomeScreen
import com.statushub.ui.instagram.InstagramProfileScreen
import com.statushub.ui.instagram.InstagramSearchScreen
import com.statushub.viewmodel.InstagramProfileViewModel
import com.statushub.ui.preview.PreviewScreen
import com.statushub.ui.splash.SplashScreen
import com.statushub.viewmodel.MainViewModel
import com.statushub.viewmodel.SplashViewModel
import java.net.URLEncoder

/**
 * Navigation routes
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Main : Screen("main")
    object InstagramSearch : Screen("instagram/search")
    object InstagramProfile : Screen("instagram/profile/{username}?userId={userId}&pic={pic}") {
        fun createRoute(username: String, userId: String? = null, pictureUrl: String? = null) =
            "instagram/profile/${enc(username)}?userId=${enc(userId.orEmpty())}&pic=${enc(pictureUrl.orEmpty())}"

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    }
    object Preview : Screen("preview/{statusUri}") {
        fun createRoute(statusUri: String) = "preview/${URLEncoder.encode(statusUri, "UTF-8")}"
    }
}

/**
 * Navigation graph for the app
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    splashViewModel: SplashViewModel,
    mainViewModel: MainViewModel,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        // Pushed screens slide in from the right with a fade; back reverses it
        enterTransition = {
            slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))
        },
        exitTransition = {
            slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(220))
        },
        popEnterTransition = {
            slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(320))
        },
        popExitTransition = {
            slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(220))
        }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen()
            
            // Navigate to main screen after splash
            val isLoading by splashViewModel.isLoading.collectAsState()
            val context = LocalContext.current
            LaunchedEffect(isLoading) {
                if (!isLoading) {
                    // First launch: show the welcome tour once
                    val next = if (PreferencesHelper.isOnboardingDone(context)) Screen.Main else Screen.Onboarding
                    navController.navigate(next.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            }
        }

        composable(
            Screen.Onboarding.route,
            exitTransition = { fadeOut(tween(400)) + scaleOut(targetScale = 1.08f, animationSpec = tween(400)) }
        ) {
            val context = LocalContext.current
            OnboardingScreen(
                onFinish = {
                    PreferencesHelper.setOnboardingDone(context)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Main.route) {
            HomeScreen(
                mainViewModel = mainViewModel,
                sharedText = sharedText,
                onSharedTextConsumed = onSharedTextConsumed,
                onOpenInstagramProfile = { user ->
                    navController.navigate(Screen.InstagramProfile.createRoute(user.username, user.id, user.profilePicUrl))
                },
                onOpenInstagramSearch = { navController.navigate(Screen.InstagramSearch.route) },
                onNavigateToPreview = { statusItem ->
                    try {
                        navController.navigate(Screen.Preview.createRoute(statusItem.uri.toString()))
                    } catch (e: Exception) {
                        android.util.Log.e("NavGraph", "Navigation error: ${e.message}", e)
                    }
                }
            )
        }
        
        composable(Screen.InstagramSearch.route) {
            InstagramSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { username, user ->
                    navController.navigate(Screen.InstagramProfile.createRoute(username, user?.id, user?.profilePicUrl))
                }
            )
        }

        composable(
            route = Screen.InstagramProfile.route,
            arguments = listOf(
                navArgument(InstagramProfileViewModel.ARG_USERNAME) { type = NavType.StringType },
                navArgument(InstagramProfileViewModel.ARG_USER_ID) { type = NavType.StringType; defaultValue = "" },
                navArgument(InstagramProfileViewModel.ARG_PICTURE) { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            InstagramProfileScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("statusUri") { type = NavType.StringType })
        ) { backStackEntry ->
            // Navigation already decodes the argument once; decoding again would corrupt %2F in content Uris
            val statusUri = backStackEntry.arguments?.getString("statusUri") ?: ""
            val context = LocalContext.current
            val statusList = remember(statusUri) { mainViewModel.statusListFor(statusUri) }
            val savingUris by mainViewModel.savingUris.collectAsState()
            val savedUris by mainViewModel.savedUris.collectAsState()

            if (statusList != null) {
                PreviewScreen(
                    items = statusList.first,
                    initialIndex = statusList.second,
                    savingUris = savingUris,
                    savedUris = savedUris,
                    onSave = { item ->
                        mainViewModel.saveStatus(item) { saved ->
                            // Full-screen ads only ever come after a finished save, never before
                            if (saved) Engagement.onSaveCompleted(context as? android.app.Activity, Engagement.KIND_STATUS)
                            if (!saved) {
                                android.widget.Toast.makeText(context, com.statushub.R.string.status_save_failed, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            } else {
                // Status list was lost (e.g. process restart) - go back to the grid
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}
