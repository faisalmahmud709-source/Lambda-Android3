package com.lambda.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambda.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Start-up animation: green → black gradient, the logo fades in at the centre, then slides a little to the
 * left while "Track to Trace" appears beside it. Calls [onFinished] when done.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    var showText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // fade + settle the logo in the middle of the screen
        val a = launch { logoAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        logoScale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        a.join()
        delay(450)
        showText = true            // logo slides left as the title grows in beside it
        delay(1500)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF06452A), Color(0xFF021A10), Color.Black),
                start = Offset(0f, 0f), end = Offset(1400f, 2400f)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.lambda_mark), "Logo",
                Modifier.size(132.dp).scale(logoScale.value).alpha(logoAlpha.value)
            )
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(700)) + expandHorizontally(tween(800, easing = FastOutSlowInEasing), expandFrom = Alignment.Start)
            ) {
                Text(
                    "Track to Trace", color = Color.White, fontFamily = DisplayFont, fontStyle = FontStyle.Italic,
                    fontSize = 28.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
