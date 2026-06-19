package com.tingmusic.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tingmusic.ui.theme.RB

/** 红色脉动均衡器(3 根),标当前播放曲。 */
@Composable
fun EqualizerBars(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val phases = listOf(0, 300, 600)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        phases.forEachIndexed { i, delayMs ->
            val h by t.animateFloat(
                4f, 14f,
                infiniteRepeatable(tween(900, delayMillis = delayMs), RepeatMode.Reverse),
                label = "b$i",
            )
            if (i > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.width(2.dp).height(h.dp).clip(CircleShape)) {
                Canvas(Modifier.size(2.dp, h.dp)) { drawRect(RB.Red) }
            }
        }
    }
}

/** mini 条播放/暂停:外圈 conic 进度环 + 内圆 + 图标。 */
@Composable
fun ConicPlayButton(progress: Float, isPlaying: Boolean, onClick: () -> Unit, sizeDp: Int = 38) {
    val sweep = 360f * progress.coerceIn(0f, 1f)
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawArc(
                Color(0x21FFFFFF), 0f, 360f, useCenter = false,
                style = Stroke(width = size.minDimension * 0.11f),
            )
            drawArc(
                RB.Red, -90f, sweep, useCenter = false,
                style = Stroke(width = size.minDimension * 0.11f),
            )
        }
        Box(
            Modifier
                .size((sizeDp * 0.79f).dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size((sizeDp * 0.79f).dp)) { drawCircle(RB.MiniBg) }
            if (isPlaying) {
                Icon(Icons.Filled.Pause, "暂停", tint = RB.Text, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Filled.PlayArrow, "播放", tint = RB.Text, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 列表"播放全部"用的红色描边圈 + 实心三角。 */
@Composable
fun RedCirclePlay(sizeDp: Int = 24) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(RB.Red, radius = r - 1f, center = c, style = Stroke(width = 1.5f * density))
        // 实心三角(指向右)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - r * 0.18f, c.y - r * 0.34f)
            lineTo(c.x + r * 0.42f, c.y)
            lineTo(c.x - r * 0.18f, c.y + r * 0.34f)
            close()
        }
        drawPath(p, RB.Red)
    }
}
