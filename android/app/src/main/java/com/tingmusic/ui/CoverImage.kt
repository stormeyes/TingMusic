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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track
import com.tingmusic.sync.CoverFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 取封面:内嵌 → iTunes(带缓存)→ null。全程 IO 线程。 */
@Composable
private fun rememberCover(track: Track?): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = track?.id) {
        value = null
        val t = track ?: return@produceState
        // 1) 内嵌
        val embedded = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(t.file.absolutePath)
                    mmr.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                } finally { runCatching { mmr.release() } }
            }.getOrNull()
        }
        if (embedded != null) { value = embedded; return@produceState }
        // 2) iTunes(带本地缓存)
        value = CoverFetcher(context).fetch(t.title, t.artist, t.id)
    }
    return bmp
}

@Composable
fun CoverImage(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48,
    vinylFrame: Boolean = false,
) {
    val cover = rememberCover(track)
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val spin = if (vinylFrame && isPlaying) Modifier.rotate(angle) else Modifier
    Box(modifier.size(sizeDp.dp).clip(CircleShape)) {
        if (cover != null) {
            if (vinylFrame) {
                // 封面嵌进黑胶圈:外圈深色 + 中间封面圆
                Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                }
                Image(
                    cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size((sizeDp * 0.66f).dp).clip(CircleShape).then(spin),
                )
            } else {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
            }
        } else {
            Canvas(Modifier.size(sizeDp.dp).then(spin)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF0A0A0A), radius = r, center = c)
                drawCircle(Color(0x14FFFFFF), radius = r * 0.8f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0x14FFFFFF), radius = r * 0.6f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0xFFD33A31), radius = r * 0.3f, center = c)
                drawCircle(Color(0xFF0A0A0A), radius = r * 0.05f, center = c)
            }
        }
    }
}
