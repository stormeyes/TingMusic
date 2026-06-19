package com.tingmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tingmusic.library.Track
import com.tingmusic.playback.PlayMode
import com.tingmusic.playback.PlaybackState
import com.tingmusic.ui.theme.RB

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    track: Track,
    state: PlaybackState,
    livePositionMs: Long,
    cover: ImageBitmap?,
    onClose: () -> Unit,
    onOpenLyrics: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
) {
    val dur = if (state.durationMs > 0) state.durationMs else track.durationMs

    // Fix 1: blurred cover background + dark scrim
    Box(Modifier.fillMaxSize().background(RB.Bg)) {
        // Layer 1: blurred cover art
        if (cover != null) {
            Image(
                cover, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f },
            )
        }
        // Layer 2: dark scrim (~0.76 opacity black)
        Box(Modifier.fillMaxSize().background(Color(0xC2000000)))
        // Layer 3: player UI
        Column(
            Modifier.fillMaxSize(),
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "收起", tint = RB.Text)
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = RB.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(track.artist, fontSize = 11.sp, color = RB.TextDim, maxLines = 1)
                }
                Icon(
                    Icons.Filled.MoreVert, null,
                    tint = RB.Text, modifier = Modifier.size(24.dp).padding(4.dp),
                )
            }

            // Fix 2: vinyl disc fills near-full width via BoxWithConstraints
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpenLyrics() },
                contentAlignment = Alignment.Center,
            ) {
                val discSize = (minOf(maxWidth, maxHeight) * 0.82f)
                VinylDisc(cover = cover, isPlaying = state.isPlaying, sizeDp = discSize.value.toInt())
            }

            // 信息 + 控制
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    track.title,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RB.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.artist,
                    fontSize = 13.5.sp,
                    color = Color(0x80FFFFFF), // white @ 50%
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )

                Spacer(Modifier.height(28.dp))

                // 进度行
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmt(livePositionMs), fontSize = 11.sp, color = RB.TextDim)
                    // Fix 3: round red 13dp thumb + thin 3dp track
                    Slider(
                        value = if (dur > 0) (livePositionMs.toFloat() / dur) else 0f,
                        onValueChange = { onSeek((it * dur).toLong()) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = RB.Red,
                            activeTrackColor = RB.Red,
                            inactiveTrackColor = Color(0x24FFFFFF),
                        ),
                        thumb = {
                            Box(
                                Modifier.size(13.dp).clip(CircleShape).background(RB.Red),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(3.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = RB.Red,
                                    inactiveTrackColor = Color(0x24FFFFFF),
                                ),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null,
                            )
                        },
                    )
                    Text(fmt(dur), fontSize = 11.sp, color = RB.TextDim)
                }

                Spacer(Modifier.height(20.dp))

                // 传输控制行
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左:expand 装饰
                    Icon(
                        Icons.Filled.Fullscreen, null,
                        tint = Color(0xA6FFFFFF), // white@65%
                        modifier = Modifier.size(24.dp),
                    )

                    // 上一首
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Filled.SkipPrevious, "上一首", tint = RB.Text, modifier = Modifier.size(30.dp))
                    }

                    // 大红播放键 68dp
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(RB.Red)
                            .clickable { onToggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isPlaying) {
                            Icon(Icons.Filled.Pause, "暂停", tint = Color.White, modifier = Modifier.size(26.dp))
                        } else {
                            Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }

                    // 下一首
                    IconButton(onClick = onNext) {
                        Icon(Icons.Filled.SkipNext, "下一首", tint = RB.Text, modifier = Modifier.size(30.dp))
                    }

                    // 右:repeat / shuffle 循环模式
                    IconButton(onClick = onCycleMode) {
                        val icon = when (state.mode) {
                            PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
                            PlayMode.RANDOM -> Icons.Filled.Shuffle
                            PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        }
                        val tint = if (state.mode != PlayMode.SEQUENTIAL) RB.Red else Color(0xA6FFFFFF)
                        Icon(icon, "循环模式", tint = tint, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
