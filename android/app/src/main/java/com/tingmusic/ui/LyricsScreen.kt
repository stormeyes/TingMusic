package com.tingmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.tingmusic.library.LrcParser
import com.tingmusic.library.Lyrics
import com.tingmusic.library.Track
import com.tingmusic.playback.LyricsIndex
import com.tingmusic.ui.theme.RB

@Composable
fun LyricsScreen(
    track: Track,
    livePositionMs: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    val cover = rememberCover(track)

    Box(Modifier.fillMaxSize().background(RB.Bg)) {
        // 背景:模糊放大封面 + 暗罩
        if (cover != null) {
            Image(
                cover, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(46.dp)
                    .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0xD7080808)))

        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, "返回", tint = RB.Text)
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = RB.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(track.artist, fontSize = 11.sp, color = RB.TextDim, maxLines = 1)
                }
                // 占位保持居中
                Spacer(Modifier.width(48.dp))
            }

            // 歌词区
            val lyrics = remember(track) {
                track.lrcFile?.takeIf { it.isFile }?.let {
                    runCatching { LrcParser.parse(it.readText()) }.getOrNull()
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onBack() },
            ) {
                when (lyrics) {
                    is Lyrics.Synced -> {
                        val lines = lyrics.lines
                        val active = LyricsIndex.activeIndex(lines, livePositionMs)
                        val listState = rememberLazyListState()
                        LaunchedEffect(active) {
                            if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0))
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 60.dp),
                        ) {
                            items(lines.size) { i ->
                                Text(
                                    lines[i].text.ifBlank { "♪" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 24.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (i == active) Color.White else Color(0x4DFFFFFF),
                                    fontSize = if (i == active) 17.sp else 14.5.sp,
                                    fontWeight = if (i == active) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    is Lyrics.Plain -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                lyrics.text,
                                color = RB.TextDim,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }

                    null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无歌词", fontSize = 15.sp, color = RB.TextDim)
                        }
                    }
                }
            }

            // 底部控制
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 0.dp)
                    .padding(bottom = 22.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Filled.SkipPrevious, "上一首", tint = RB.Text, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(40.dp))
                // 红播放键 58dp
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(RB.Red)
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        Icon(Icons.Filled.Pause, "暂停", tint = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(40.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, "下一首", tint = RB.Text, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
