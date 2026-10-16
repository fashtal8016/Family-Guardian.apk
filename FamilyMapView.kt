package com.example.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationRecord
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FamilyMapView(
    currentLocation: LocationRecord?,
    history: List<LocationRecord>,
    childName: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("family_map_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.6f, 3.5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f + offsetX, h / 2f + offsetY)

                // Background Map Grid & Roads
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                    )
                )

                // Road grid patterns
                val gridSpacing = 60f * scale
                val startX = (center.x % gridSpacing)
                val startY = (center.y % gridSpacing)

                var x = startX
                while (x < w) {
                    drawLine(
                        color = Color(0xFFFFFFFF),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 3f * scale
                    )
                    x += gridSpacing
                }

                var y = startY
                while (y < h) {
                    drawLine(
                        color = Color(0xFFFFFFFF),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 3f * scale
                    )
                    y += gridSpacing
                }

                // Decorative river/avenue curve
                val riverPath = Path().apply {
                    moveTo(0f, h * 0.7f + offsetY * 0.5f)
                    cubicTo(
                        w * 0.3f, h * 0.5f + offsetY * 0.5f,
                        w * 0.7f, h * 0.8f + offsetY * 0.5f,
                        w, h * 0.4f + offsetY * 0.5f
                    )
                }
                drawPath(
                    path = riverPath,
                    color = Color(0xFF93C5FD).copy(alpha = 0.6f),
                    style = Stroke(width = 16f * scale, cap = StrokeCap.Round)
                )

                // Draw historical location path
                if (history.size > 1 && currentLocation != null) {
                    val path = Path()
                    val lat0 = currentLocation.latitude
                    val lon0 = currentLocation.longitude

                    val points = history.take(15).map { rec ->
                        val dx = ((rec.longitude - lon0) * 12000f * scale).toFloat()
                        val dy = (-(rec.latitude - lat0) * 12000f * scale).toFloat()
                        Offset(center.x + dx, center.y + dy)
                    }

                    if (points.isNotEmpty()) {
                        path.moveTo(points.first().x, points.first().y)
                        for (pt in points.drop(1)) {
                            path.lineTo(pt.x, pt.y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF2563EB),
                            style = Stroke(width = 4f * scale, cap = StrokeCap.Round)
                        )

                        // Breadcrumb dots
                        for (pt in points) {
                            drawCircle(
                                color = Color(0xFF1E3A8A),
                                radius = 4f * scale,
                                center = pt
                            )
                        }
                    }
                }

                // GPS Accuracy Radius circle
                val accuracyRadius = ((currentLocation?.accuracy?.toFloat() ?: 15f) * 1.5f * scale)
                    .coerceIn(25f, 150f)
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                    radius = accuracyRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF2563EB).copy(alpha = 0.4f),
                    radius = accuracyRadius,
                    center = center,
                    style = Stroke(width = 2f)
                )

                // Animated Radar Pulse
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                    radius = pulseRadius * scale,
                    center = center
                )

                // Center Pin Base & Shadow
                drawCircle(
                    color = Color(0x33000000),
                    radius = 16f * scale,
                    center = center.copy(y = center.y + 4f)
                )

                // Main Marker Pin
                drawCircle(
                    color = Color(0xFF1E3A8A),
                    radius = 14f * scale,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = 9f * scale,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f * scale,
                    center = center
                )
            }

            // Top Info Overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF16A34A), CircleShape)
                    )
                    Column {
                        Text(
                            text = "موقعیت $childName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentLocation != null) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "عرض: %.4f | طول: %.4f | دقت: ±%.1fm",
                                    currentLocation.latitude,
                                    currentLocation.longitude,
                                    currentLocation.accuracy
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = "در انتظار دریافت مختصات...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Map Controls (Zoom In, Zoom Out, Recenter)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = { scale = (scale * 1.25f).coerceAtMost(3.5f) },
                    modifier = Modifier.size(38.dp).testTag("map_zoom_in"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "بزرگ‌نمایی", tint = MaterialTheme.colorScheme.primary)
                }
                FilledIconButton(
                    onClick = { scale = (scale / 1.25f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(38.dp).testTag("map_zoom_out"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "کوچک‌نمایی", tint = MaterialTheme.colorScheme.primary)
                }
                FilledIconButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier.size(38.dp).testTag("map_recenter"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "بازنشانی مرکز", tint = Color.White)
                }
            }
        }
    }
}
