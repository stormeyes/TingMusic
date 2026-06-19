package com.tingmusic.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * 网易云风黑胶:厚黑胶外圈 + 中心圆形封面 + 白色长唱臂(转轴水平居中、在唱片上方
 * 留距)。播放:唱针落到唱片中间;暂停:整条臂甩到碟面右外侧。碟播放时 22s/圈旋转,
 * 暂停冻结角度。容器比唱片宽/高一些,给唱臂转轴和甩出留空间。cover 由调用方传入。
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
    // 唱臂:播放=唱针落碟心(0°),暂停=甩出碟外右侧(-55°),绕居中转轴旋转
    val arm by animateFloatAsState(if (isPlaying) 0f else -68f, animationSpec = tween(550), label = "arm")

    // 容器:宽 = 唱片 ×1.30(给唱臂左右留空),高 = ×1.32(给转轴上方留空);唱片底部居中
    Box(
        Modifier.width((sizeDp * 1.30f).dp).height((sizeDp * 1.32f).dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // ── 旋转的黑胶 + 封面 ──
        Box(
            Modifier.size(sizeDp.dp).graphicsLayer { rotationZ = angle },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f * 0.96f
                drawCircle(Color(0xFF131313), r, c)
                var rr = r * 0.985f
                var i = 0
                while (rr > r * 0.52f) {
                    drawCircle(
                        if (i % 2 == 0) Color(0xFF1E1E1E) else Color(0xFF0D0D0D),
                        rr, c, style = Stroke(width = r * 0.006f),
                    )
                    rr -= r * 0.020f
                    i++
                }
            }
            Box(Modifier.fillMaxSize(0.70f).clip(CircleShape), contentAlignment = Alignment.Center) {
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
            Canvas(Modifier.fillMaxSize(0.70f)) {
                drawCircle(
                    Color(0x33FFFFFF), size.minDimension / 2f,
                    Offset(size.width / 2f, size.height / 2f), style = Stroke(2f),
                )
            }
            Canvas(Modifier.size(7.dp)) {
                drawCircle(Color(0xFF0A0A0A), size.minDimension / 2f, Offset(size.width / 2f, size.height / 2f))
            }
        }

        // ── 白色长唱臂(几何按唱片计算),绕居中转轴旋转 ──
        Canvas(Modifier.fillMaxSize()) {
            val discD = size.height / 1.32f          // 唱片直径
            val u = discD / 250f
            val cx = size.width / 2f                 // 水平中心(转轴在此,唱片也居中)
            val discTop = size.height - discD
            val discCenterY = size.height - discD / 2f
            val white = Color(0xFFF0F0F0)
            val edge = Color(0xFFBFBFBF)
            val pivot = Offset(cx, discTop - discD * 0.12f)          // 转轴:唱片上方留距
            val elbow = Offset(cx - discD * 0.015f, pivot.y + (discCenterY - pivot.y) * 0.55f)
            val head = Offset(cx - discD * 0.04f, discCenterY - discD * 0.06f)  // 唱针:落在唱片中间
            rotate(degrees = arm, pivot = pivot) {
                drawLine(white, pivot, elbow, strokeWidth = 6.5f * u, cap = StrokeCap.Round)
                drawLine(white, elbow, head, strokeWidth = 6.5f * u, cap = StrokeCap.Round)
                drawCircle(white, 13f * u, pivot)
                drawCircle(edge, 13f * u, pivot, style = Stroke(1.5f))
                drawCircle(Color(0xFF9A9A9A), 4.5f * u, pivot)
                // 唱头卡座
                rotate(degrees = 22f, pivot = head) {
                    drawRoundRect(
                        white,
                        topLeft = Offset(head.x - 9f * u, head.y - 3f * u),
                        size = Size(18f * u, 30f * u),
                        cornerRadius = CornerRadius(4f * u, 4f * u),
                    )
                    drawRoundRect(
                        edge,
                        topLeft = Offset(head.x - 9f * u, head.y - 3f * u),
                        size = Size(18f * u, 30f * u),
                        cornerRadius = CornerRadius(4f * u, 4f * u),
                        style = Stroke(1f),
                    )
                }
            }
        }
    }
}
