package com.tingmusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tingmusic.ui.theme.RB

/**
 * 网易云风黑胶:厚黑胶外圈 + 中心圆形封面(约 0.6,留厚黑胶环)+ 白色唱臂
 * (顶部转轴球 + 弯臂 + 唱头)。播放时碟旋转(22s/圈,暂停冻结角度);
 * 唱臂播放时落在碟上、暂停时抬起。cover 由调用方传入。
 */
@Composable
fun VinylDisc(cover: ImageBitmap?, isPlaying: Boolean, sizeDp: Int = 250) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle = (angle + (now - last) / 1_000_000_000f / 22f * 360f) % 360f
            last = now
        }
    }
    // 唱臂:播放=落到碟面(0°),暂停=抬起外移(-30°),绕顶部转轴旋转
    val arm by animateFloatAsState(if (isPlaying) 0f else -30f, animationSpec = tween(550), label = "arm")

    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        // ── 旋转的黑胶 + 封面 ──
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = angle },
            contentAlignment = Alignment.Center,
        ) {
            // 黑胶碟体 + 凹槽
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f * 0.96f
                drawCircle(Color(0xFF131313), r, c)
                var rr = r * 0.985f
                var i = 0
                while (rr > r * 0.40f) {
                    drawCircle(
                        if (i % 2 == 0) Color(0xFF1E1E1E) else Color(0xFF0D0D0D),
                        rr, c, style = Stroke(width = r * 0.006f),
                    )
                    rr -= r * 0.020f
                    i++
                }
            }
            // 中心圆形封面(约 0.6 直径)
            Box(Modifier.fillMaxSize(0.60f).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (cover != null) {
                    Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(Color(0xFF222222), size.minDimension / 2f, c)
                        drawCircle(RB.Red, size.minDimension * 0.16f, c)
                    }
                }
            }
            // 封面外缘细环
            Canvas(Modifier.fillMaxSize(0.60f)) {
                drawCircle(
                    Color(0x33FFFFFF), size.minDimension / 2f,
                    Offset(size.width / 2f, size.height / 2f), style = Stroke(2f),
                )
            }
            // 轴孔
            Canvas(Modifier.size(7.dp)) {
                drawCircle(Color(0xFF0A0A0A), size.minDimension / 2f, Offset(size.width / 2f, size.height / 2f))
            }
        }

        // ── 白色唱臂(网易云风),绕顶部转轴旋转(不随碟自转)──
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val u = size.minDimension / 250f
            val pivot = Offset(w * 0.63f, size.height * 0.05f)
            val white = Color(0xFFF0F0F0)
            val edge = Color(0xFFBFBFBF)
            rotate(degrees = arm, pivot = pivot) {
                // 臂杆:转轴 → 弯肘 → 唱头(两段,略带弯)
                val elbow = Offset(pivot.x - 4f * u, pivot.y + 58f * u)
                val head = Offset(pivot.x - 26f * u, pivot.y + 96f * u)
                drawLine(white, pivot, elbow, strokeWidth = 7f * u, cap = StrokeCap.Round)
                drawLine(white, elbow, head, strokeWidth = 7f * u, cap = StrokeCap.Round)
                // 转轴底座
                drawCircle(white, 13f * u, pivot)
                drawCircle(edge, 13f * u, pivot, style = Stroke(1.5f))
                drawCircle(Color(0xFF9A9A9A), 4f * u, pivot)
                // 唱头(白色卡座),略转一点对齐臂尾
                rotate(degrees = 20f, pivot = head) {
                    drawRoundRect(
                        white,
                        topLeft = Offset(head.x - 9f * u, head.y - 2f * u),
                        size = Size(18f * u, 28f * u),
                        cornerRadius = CornerRadius(4f * u, 4f * u),
                    )
                    drawRoundRect(
                        edge,
                        topLeft = Offset(head.x - 9f * u, head.y - 2f * u),
                        size = Size(18f * u, 28f * u),
                        cornerRadius = CornerRadius(4f * u, 4f * u),
                        style = Stroke(1f),
                    )
                }
            }
        }
    }
}
