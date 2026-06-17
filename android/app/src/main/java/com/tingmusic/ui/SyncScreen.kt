package com.tingmusic.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.sync.SyncProgress

@Composable
fun SyncScreen(vm: SyncViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsStateWithLifecycle()

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
        Text("曲库(${s.tracks.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(s.tracks, key = { it.id }) { t ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
