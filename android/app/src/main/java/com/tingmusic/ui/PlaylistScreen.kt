package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingmusic.library.Track

@Composable
fun PlaylistScreen(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text("曲库是空的", style = MaterialTheme.typography.titleMedium)
            Text("打开左上角抽屉,扫描局域网内的 Mac 同步曲库。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(t) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(track = t, isPlaying = false, sizeDp = 48)
                Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (t.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("${t.artist}${if (t.lrcFile != null) "  · 有歌词" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
