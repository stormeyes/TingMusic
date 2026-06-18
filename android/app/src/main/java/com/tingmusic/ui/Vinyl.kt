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
import androidx.compose.runtime.mutableStateOf
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
import com.tingmusic.library.Track

/** 旋转黑胶:深色胶圈 + 封面(或黑胶占位)+ 右上唱臂(播放落下/暂停抬起)。 */
@Composable
fun VinylDisc(track: Track, isPlaying: Boolean, cover: ImageBitmap?, sizeDp: Int = 260) {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle = (angle + (now - last) / 1_000_000_000f / 20f * 360f) % 360f  // 20s / rev
            last = now
        }
    }
    // always applied; holds angle when paused
    val spin = Modifier.graphicsLayer { rotationZ = angle }
    // 唱臂角度:播放 -2°(落到碟面),暂停 -22°(抬起)
    val armAngle by animateFloatAsState(if (isPlaying) -2f else -22f, label = "arm")

    Box(Modifier.size((sizeDp * 1.15f).dp), contentAlignment = Alignment.Center) {
        // 黑胶圈
        Canvas(Modifier.size(sizeDp.dp).then(spin)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
            drawCircle(Color(0x14FFFFFF), radius = r * 0.92f, center = c, style = Stroke(r * 0.012f))
            drawCircle(Color(0x14FFFFFF), radius = r * 0.80f, center = c, style = Stroke(r * 0.012f))
        }
        // 封面(或占位红心)嵌在中间
        if (cover != null) {
            Image(cover, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size((sizeDp * 0.6f).dp).clip(CircleShape).then(spin))
        } else {
            Canvas(Modifier.size((sizeDp * 0.6f).dp).then(spin)) {
                val r = size.minDimension / 2f; val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFFD33A31), radius = r * 0.5f, center = c)
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.08f, center = c)
            }
        }
        // 唱臂:从右上角支点伸向碟心,绕支点旋转
        Canvas(Modifier.size((sizeDp * 1.15f).dp)) {
            val pivot = Offset(size.width * 0.82f, size.height * 0.12f)
            val len = size.minDimension * 0.42f
            rotate(degrees = armAngle, pivot = pivot) {
                drawCircle(Color(0xFF888888), radius = size.minDimension * 0.03f, center = pivot)
                drawLine(Color(0xFFB0B0B0), start = pivot,
                    end = Offset(pivot.x - len * 0.5f, pivot.y + len), strokeWidth = size.minDimension * 0.018f)
            }
        }
    }
}
