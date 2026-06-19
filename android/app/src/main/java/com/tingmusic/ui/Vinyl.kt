package com.tingmusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tingmusic.ui.theme.RB

/**
 * 凹槽黑胶 + 精致唱臂。播放时 18s/圈,暂停冻结当前角度;唱臂播放 26° / 暂停 10°。
 * cover 由调用方一次性 rememberCover 后传入(避免重复 IO)。
 */
@Composable
fun VinylDisc(cover: ImageBitmap?, isPlaying: Boolean, sizeDp: Int = 250) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle = (angle + (now - last) / 1_000_000_000f / 18f * 360f) % 360f
            last = now
        }
    }
    val spin = Modifier.graphicsLayer { rotationZ = angle }
    val armAngle by animateFloatAsState(if (isPlaying) 26f else 10f, label = "arm")

    // 容器留出唱臂空间
    Box(Modifier.size((sizeDp * 1.2f).dp), contentAlignment = Alignment.Center) {
        // 凹槽碟体
        Canvas(Modifier.size(sizeDp.dp).then(spin)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF101010), radius = r, center = c)
            // 同心凹槽(深浅交替)
            var rr = r * 0.95f
            var i = 0
            while (rr > r * 0.46f) {
                drawCircle(
                    if (i % 2 == 0) Color(0xFF191919) else Color(0xFF0C0C0C),
                    radius = rr, center = c, style = Stroke(width = r * 0.012f),
                )
                rr -= r * 0.035f
                i++
            }
        }
        // 中心封面 106/250 比例
        val coverDp = (sizeDp * 0.424f)
        Box(
            Modifier
                .size(coverDp.dp)
                .clip(CircleShape)
                .then(spin),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    cover, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(coverDp.dp),
                )
            } else {
                Canvas(Modifier.size(coverDp.dp)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color(0xFF1A1A1A), r, c)
                    drawCircle(RB.Red, r * 0.5f, c)
                }
            }
        }
        // 中心轴孔
        Canvas(Modifier.size(11.dp)) {
            drawCircle(RB.Bg, size.minDimension / 2f, Offset(size.width / 2f, size.height / 2f))
            drawCircle(
                Color(0x40FFFFFF), size.minDimension / 2f,
                Offset(size.width / 2f, size.height / 2f), style = Stroke(1f),
            )
        }
        // 唱臂:支点在容器右上,绕支点旋转
        Canvas(Modifier.size((sizeDp * 1.2f).dp)) {
            val pivot = Offset(size.width * 0.84f, size.height * 0.10f)
            val unit = size.minDimension / 250f  // 把设计的 px 换算到当前尺寸
            rotate(degrees = armAngle, pivot = pivot) {
                // 臂杆
                drawLine(
                    Color(0xFF7A7A7A), pivot,
                    Offset(pivot.x, pivot.y + 118f * unit), strokeWidth = 4f * unit,
                )
                // 转轴底座
                drawCircle(Color(0xFF222222), 13f * unit, pivot)
                drawCircle(Color(0x1FFFFFFF), 13f * unit, pivot, style = Stroke(1f))
                drawCircle(RB.Red, 5f * unit, pivot)  // 红点
                // 唱头
                val head = Offset(pivot.x, pivot.y + 126f * unit)
                drawCircle(Color(0xFF222222), 10f * unit, head)
                drawCircle(RB.Red, 2.5f * unit, Offset(head.x, head.y + 12f * unit)) // 唱针红尖
            }
        }
    }
}
