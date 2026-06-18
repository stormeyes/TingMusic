package com.tingmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingmusic.library.LrcParser
import com.tingmusic.library.Lyrics
import com.tingmusic.library.Track
import com.tingmusic.playback.LyricsIndex
import com.tingmusic.playback.PlayMode
import com.tingmusic.playback.PlaybackState

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
private fun modeLabel(m: PlayMode) = when (m) { PlayMode.SEQUENTIAL -> "顺序"; PlayMode.RANDOM -> "随机"; PlayMode.REPEAT_ONE -> "单曲" }

@Composable
fun NowPlayingScreen(
    track: Track,
    state: PlaybackState,
    livePositionMs: Long,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
) {
    var lyricsMode by remember(track.id) { mutableStateOf(false) }
    val bg = rememberCover(track)  // internal, from CoverImage.kt
    Box(Modifier.fillMaxSize()) {
        if (bg != null) {
            Image(bg, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp))  // API<31 no blur, scrim darkens
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "收起", tint = Color.White) }
            Box(Modifier.weight(1f).fillMaxWidth().clickable { lyricsMode = !lyricsMode },
                contentAlignment = Alignment.Center) {
                if (!lyricsMode) VinylDisc(track = track, isPlaying = state.isPlaying, cover = bg, sizeDp = 260)
                else LyricsView(track = track, positionMs = livePositionMs, modifier = Modifier.fillMaxHeight())
            }
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            val dur = if (state.durationMs > 0) state.durationMs else track.durationMs
            Slider(value = if (dur > 0) (livePositionMs.toFloat() / dur) else 0f, onValueChange = { onSeek((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${fmt(livePositionMs)} / ${fmt(dur)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "上一首", tint = Color.White) }
                    IconButton(onClick = onToggle) { if (state.isPlaying) Icon(Icons.Filled.Pause, "暂停", tint = Color.White) else Icon(Icons.Filled.PlayArrow, "播放", tint = Color.White) }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White) }
                }
                TextButton(onClick = onCycleMode) { Text(modeLabel(state.mode), color = Color.White) }
            }
        }
    }
}

/** 同步歌词:解析当前曲 .lrc,按 position 高亮+居中。解析按 track 记忆(避免每帧重读)。 */
@Composable
fun LyricsView(track: Track?, positionMs: Long, modifier: Modifier = Modifier) {
    val lyrics = remember(track) {
        val lrc = track?.lrcFile
        if (lrc != null && lrc.isFile) runCatching { LrcParser.parse(lrc.readText()) }.getOrNull() else null
    }
    val listState = rememberLazyListState()
    when (lyrics) {
        is Lyrics.Synced -> {
            val lines = lyrics.lines
            val active = LyricsIndex.activeIndex(lines, positionMs)
            LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0)) }
            LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
                items(lines.size) { i ->
                    Text(lines[i].text.ifBlank { "♪" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
                        color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        is Lyrics.Plain -> Text(lyrics.text, modifier.padding(16.dp))
        null -> Text("无歌词", modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
