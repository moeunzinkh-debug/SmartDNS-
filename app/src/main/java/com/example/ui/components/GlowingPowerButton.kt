package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueGlow
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun GlowingPowerButton(
    isActive: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Pulsing glowing animation
    val infiniteTransition = rememberInfiniteTransition(label = "power_glow_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.14f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 0.85f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val ringColor by animateColorAsState(
        targetValue = if (isActive) NeonCyan else Color(0xFF37474F),
        animationSpec = tween(500),
        label = "ring_color"
    )

    val innerBgBrush = if (isActive) {
        Brush.radialGradient(
            colors = listOf(
                ElectricBlue.copy(alpha = 0.95f),
                Color(0xFF003882),
                ObsidianBlack
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                SurfaceCardElevated,
                SurfaceCard,
                ObsidianBlack
            )
        )
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("one_tap_power_button"),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Canvas Rings
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width / 2 - 16.dp.toPx()

            // Outer pulse ring
            if (isActive) {
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.22f * pulseAlpha),
                    radius = baseRadius * pulseScale,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.35f * pulseAlpha),
                    radius = baseRadius * (1f + (pulseScale - 1f) * 0.5f),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Main glowing outline
            drawCircle(
                color = ringColor,
                radius = baseRadius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Inner Touchable Button
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .background(innerBgBrush)
                .border(
                    width = 2.dp,
                    color = if (isActive) NeonCyan.copy(alpha = 0.8f) else BorderSubtle,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tactile haptic feedback
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(50)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Moon Icon 🌙 with Power Icon
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Moon DND Symbol",
                        tint = if (isActive) NeonCyan else TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isLoading) "OPTIMIZING..." else if (isActive) "SMART DND ON" else "TAP TO START",
                    color = if (isActive) Color.White else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isActive) "SILENCED & BOOSTED" else "NORMAL MODE",
                    color = if (isActive) NeonCyan else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
