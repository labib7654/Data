package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R

/**
 * A beautiful, adaptive custom logo representing the Ledger (دفتر الحسابات).
 * Modified to match the application icon directly.
 */
@Composable
fun LedgerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    primaryColor: Color = Color(0xFF3F51B5),
    accentColor: Color = Color(0xFFE91E63)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, primaryColor.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ledger_icon_1779971259361),
            contentDescription = "App Logo",
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    }
}

/**
 * A watermark style full screen background that displays a subtle logo in the center 
 * with dynamic abstract waves to make the app background look extremely high-end.
 */
@Composable
fun WatermarkLedgerBackground(
    modifier: Modifier = Modifier,
    alpha: Float = 0.04f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Draw abstract concentric finance orbits/waves
            drawCircle(
                color = Color(0xFF3F51B5).copy(alpha = alpha),
                radius = width * 0.6f,
                center = Offset(width / 2, height / 2),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(0xFF3F51B5).copy(alpha = alpha * 0.7f),
                radius = width * 0.8f,
                center = Offset(width / 2, height / 2),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Color(0xFFE91E63).copy(alpha = alpha * 0.5f),
                radius = width * 0.4f,
                center = Offset(width / 2, height / 2),
                style = Stroke(width = 1f)
            )
        }
    }
}

/**
 * A luxury custom progress loading layout with an infinite rotating orbit 
 * around our trademark LedgerLogo. This looks highly professional during authentication processes.
 */
@Composable
fun SpinningLedgerLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    loadingText: String = "جاري الحساب والتحقق..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotating_orbit")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            // Spinning outer glowing border
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(angle)
            ) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF3F51B5),
                            Color(0xFFE91E63),
                            Color(0xFF3F51B5).copy(alpha = 0.1f)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
            }

            // Central logo with gentle breathing pulse animation
            LedgerLogo(
                size = size * 0.75f,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        if (loadingText.isNotBlank()) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = loadingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF3F51B5),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}
