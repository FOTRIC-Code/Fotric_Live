package com.irtek.live.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1000)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val brandBlue = Color(0xFF0470FF)
    val brandBlue2 = Color(0xFF0688FD)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // Radial gradient glow at bottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 1.14f)
            val radius = size.height * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        brandBlue.copy(alpha = 0.35f * alpha),
                        brandBlue.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius,
                style = Fill
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-60).dp)
        ) {
            // Eye logo drawn with Canvas
            Canvas(
                modifier = Modifier.size(120.dp)
            ) {
                val w = size.width
                val h = size.height
                val scale = w / 100f

                // Outer eye C-shape
                val outerPath = Path().apply {
                    // Scaled from SVG coordinates (original ~80x80 centered)
                    moveTo(69.4f * scale, 18.7f * scale)
                    cubicTo(70.0f * scale, 19.3f * scale, 72.9f * scale, 22.7f * scale, 72.8f * scale, 23.4f * scale)
                    lineTo(66.8f * scale, 27.2f * scale)
                    cubicTo(66.7f * scale, 27.5f * scale, 67.9f * scale, 29.2f * scale, 68.2f * scale, 29.7f * scale)
                    cubicTo(70.0f * scale, 33.2f * scale, 70.2f * scale, 37.1f * scale, 67.3f * scale, 40.2f * scale)
                    cubicTo(66.9f * scale, 40.7f * scale, 64.7f * scale, 42.5f * scale, 64.1f * scale, 42.4f * scale)
                    lineTo(59.8f * scale, 40.0f * scale)
                    cubicTo(59.5f * scale, 39.6f * scale, 60.9f * scale, 39.2f * scale, 61.2f * scale, 39.1f * scale)
                    cubicTo(68.4f * scale, 35.3f * scale, 67.5f * scale, 24.4f * scale, 59.6f * scale, 22.0f * scale)
                    cubicTo(51.7f * scale, 19.6f * scale, 45.6f * scale, 26.9f * scale, 48.1f * scale, 34.4f * scale)
                    cubicTo(50.7f * scale, 42.4f * scale, 60.6f * scale, 44.8f * scale, 67.9f * scale, 42.6f * scale)
                    cubicTo(68.9f * scale, 42.3f * scale, 69.8f * scale, 41.8f * scale, 70.8f * scale, 41.5f * scale)
                    lineTo(69.6f * scale, 42.9f * scale)
                    cubicTo(57.0f * scale, 55.1f * scale, 36.2f * scale, 43.2f * scale, 41.0f * scale, 26.1f * scale)
                    cubicTo(44.5f * scale, 13.9f * scale, 60.1f * scale, 9.7f * scale, 69.4f * scale, 18.7f * scale)
                    close()
                }
                drawPath(
                    path = outerPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(brandBlue, brandBlue2)
                    )
                )

                // Inner pupil
                val pupilPath = Path().apply {
                    moveTo(61.2f * scale, 28.9f * scale)
                    cubicTo(64.3f * scale, 33.9f * scale, 58.2f * scale, 39.2f * scale, 53.5f * scale, 35.8f * scale)
                    cubicTo(51.7f * scale, 34.6f * scale, 50.9f * scale, 32.5f * scale, 51.4f * scale, 30.4f * scale)
                    cubicTo(51.5f * scale, 30.2f * scale, 51.8f * scale, 29.2f * scale, 52.0f * scale, 29.2f * scale)
                    cubicTo(52.2f * scale, 29.2f * scale, 52.3f * scale, 29.8f * scale, 52.4f * scale, 30.0f * scale)
                    cubicTo(53.1f * scale, 31.3f * scale, 55.0f * scale, 31.5f * scale, 56.0f * scale, 30.3f * scale)
                    cubicTo(56.7f * scale, 29.5f * scale, 56.7f * scale, 28.2f * scale, 55.9f * scale, 27.4f * scale)
                    cubicTo(55.7f * scale, 27.3f * scale, 55.1f * scale, 26.9f * scale, 55.0f * scale, 26.8f * scale)
                    cubicTo(55.0f * scale, 26.7f * scale, 55.1f * scale, 26.7f * scale, 55.1f * scale, 26.6f * scale)
                    cubicTo(55.4f * scale, 26.4f * scale, 55.8f * scale, 26.5f * scale, 56.0f * scale, 26.4f * scale)
                    cubicTo(58.3f * scale, 26.2f * scale, 60.0f * scale, 27.0f * scale, 61.2f * scale, 28.9f * scale)
                    close()
                }
                drawPath(
                    path = pupilPath,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Live View",
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black.copy(alpha = 0.55f),
                letterSpacing = 1.sp
            )
        }
    }
}
