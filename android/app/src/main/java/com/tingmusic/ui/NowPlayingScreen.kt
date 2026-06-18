package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
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
    var lyricsMode by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "收起") }
            // 中央:点击在碟 / 歌词间切换(Task 4 把碟做成旋转黑胶+唱臂+模糊背景)
            Box(Modifier.weight(1f).fillMaxWidth().clickable { lyricsMode = !lyricsMode },
                contentAlignment = Alignment.Center) {
                if (!lyricsMode) {
                    CoverImage(track = track, isPlaying = state.isPlaying, sizeDp = 260, vinylFrame = true)
                } else {
                    LyricsView(track = track, positionMs = livePositionMs)
                }
            }
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val dur = if (state.durationMs > 0) state.durationMs else track.durationMs
            Slider(value = if (dur > 0) (livePositionMs.toFloat() / dur) else 0f, onValueChange = { onSeek((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${fmt(livePositionMs)} / ${fmt(dur)}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "上一首") }
                    IconButton(onClick = onToggle) { if (state.isPlaying) Icon(Icons.Filled.Pause, "暂停") else Icon(Icons.Filled.PlayArrow, "播放") }
                    IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "下一首") }
                }
                TextButton(onClick = onCycleMode) { Text(modeLabel(state.mode)) }
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
    when (lyrics) {
        is Lyrics.Synced -> {
            val lines = lyrics.lines
            val active = LyricsIndex.activeIndex(lines, positionMs)
            val listState = rememberLazyListState()
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
