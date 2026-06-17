package com.tingmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.library.Track
import com.tingmusic.playback.PlaybackState
import com.tingmusic.sync.SyncProgress

@Composable
fun SyncScreen(
    vm: SyncViewModel,
    playback: PlaybackState,
    onPlayTrack: (Track) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    livePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showLyrics by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("局域网同步", style = MaterialTheme.typography.titleLarge)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.startDiscovery() }) { Text("扫描 Mac") }
            Button(onClick = { vm.sync(vm.manualBaseUrl()) }, enabled = !s.syncing && s.manualHost.isNotBlank()) {
                Text("手填同步")
            }
        }

        OutlinedTextField(
            value = s.manualHost,
            onValueChange = { vm.setManualHost(it) },
            label = { Text("手动 IP(如 192.168.5.139)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        s.servers.forEach { server ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${server.name} — ${server.host}:${server.port}")
                Button(onClick = { vm.sync(server.baseUrl) }, enabled = !s.syncing) { Text("同步") }
            }
        }

        s.progress?.let { p ->
            Text(progressText(p), modifier = Modifier.padding(vertical = 8.dp))
        }

        HorizontalDivider()

        val currentTrack = s.tracks.find { it.id == playback.currentId }
        NowPlayingPanel(
            track = currentTrack,
            s = playback.copy(positionMs = livePositionMs),
            onToggle = onToggle,
            onNext = onNext,
            onPrev = onPrev,
            onSeek = onSeek,
            onCycleMode = onCycleMode,
        )

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showLyrics = false }) { Text(if (!showLyrics) "● 列表" else "列表") }
            TextButton(onClick = { showLyrics = true }) { Text(if (showLyrics) "● 歌词" else "歌词") }
        }

        if (showLyrics) {
            LyricsView(currentTrack, livePositionMs, Modifier.fillMaxSize())
        } else {
            Text("曲库(${s.tracks.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(s.tracks, key = { it.id }) { t ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(t) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (t.id == playback.currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${t.artist}${if (t.lrcFile != null) "  · 有歌词" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
