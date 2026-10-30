package com.statushub.instagram

import androidx.compose.ui.res.stringResource
import com.statushub.R
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.statushub.ui.theme.SunsetPink
import com.statushub.ui.theme.StatusHubTheme

/**
 * Full-screen Instagram login using Instagram's own login page.
 *
 * Runs in its own Activity (rather than a Compose Dialog) so the keyboard and back
 * navigation inside Instagram's login flow behave like a normal browser page.
 *
 * The app never sees the password; we only detect the session cookie Instagram sets
 * after login. Cookies stay in the WebView cookie store on this device.
 * Finishes with RESULT_OK once logged in.
 */
class InstagramLoginActivity : ComponentActivity() {

    private var webView: WebView? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StatusHubTheme {
                var progress by remember { mutableIntStateOf(0) }

                // Back navigates inside the login flow first (e.g. "Forgot password"), then closes
                BackHandler {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.ig_login_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ig_cd_close))
                                }
                            },
                            actions = {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.ig_cd_secure_page),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                CookieManager.getInstance().setAcceptCookie(true)
                                WebView(context).apply {
                                    // AndroidView defaults to WRAP_CONTENT; with that WebView forces a
                                    // zero layout height, so 100vh / html{height:100%} become 0 and
                                    // Instagram's login page renders as a blank white screen.
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = InstagramClient.browserUserAgent(settings.userAgentString)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            progress = newProgress
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) {
                                            if (InstagramClient.isLoggedIn()) onLoggedIn()
                                        }
                                    }
                                    loadUrl("https://www.instagram.com/accounts/login/")
                                    webView = this
                                }
                            },
                            onRelease = { view ->
                                webView = null
                                view.destroy()
                            }
                        )

                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                color = SunsetPink,
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onLoggedIn() {
        if (isFinishing) return
        CookieManager.getInstance().flush()
        setResult(Activity.RESULT_OK)
        finish()
    }
}
