package com.hemadri.statushub.ui.splash

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hemadri.statushub.R
import com.hemadri.statushub.ui.theme.BrandGradients
import kotlinx.coroutines.launch

/**
 * Splash Screen Composable
 * Brand gradient with an animated logo; SplashViewModel decides when to move on
 */
@Composable
fun SplashScreen() {
    val logoScale = remember { Animatable(0.6f) }
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        contentAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandGradients.splash),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .size(132.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.splash_logo_description),
                    modifier = Modifier.size(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.alpha(contentAlpha.value)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha.value)
            )
        }

        Text(
            text = stringResource(R.string.splash_footer),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .alpha(contentAlpha.value)
        )
    }
}
