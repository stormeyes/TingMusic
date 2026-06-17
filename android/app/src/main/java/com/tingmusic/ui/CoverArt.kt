package com.tingmusic.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 异步取内嵌封面(IO);无则返回 null。 */
@Composable
fun rememberEmbeddedCover(file: File?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = file?.absolutePath) {
        value = null
        val f = file ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(f.absolutePath)
                    mmr.embeddedPicture?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                } finally {
                    runCatching { mmr.release() }
                }
            }.getOrNull()
        }
    }
    return bmp
}

@Composable
fun CoverArt(file: File?, isPlaying: Boolean, modifier: Modifier = Modifier, sizeDp: Int = 64) {
    val cover = rememberEmbeddedCover(file)
    // 播放时持续旋转(20s 一圈);暂停时停。
    val transition = rememberInfiniteTransition(label = "vinyl")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val spin = if (isPlaying) Modifier.rotate(angle) else Modifier
    Box(modifier.size(sizeDp.dp).clip(CircleShape)) {
        if (cover != null) {
            Image(cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp).clip(CircleShape).then(spin))
        } else {
            Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                drawCircle(Color(0x14FFFFFF), radius = r * 0.8f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0x14FFFFFF), radius = r * 0.6f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0xFFD33A31), radius = r * 0.3f, center = c) // 红色中心标签
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.05f, center = c) // 轴孔
            }
        }
    }
}
