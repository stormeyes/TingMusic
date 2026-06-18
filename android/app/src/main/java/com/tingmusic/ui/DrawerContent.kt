package com.tingmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.sync.SyncProgress
import com.tingmusic.ui.theme.AppTheme

@Composable
fun DrawerContent(vm: SyncViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    ModalDrawerSheet {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("TingMusic", style = MaterialTheme.typography.titleLarge)

            Text("主题", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(AppTheme.DEFAULT to "默认灰", AppTheme.WHITE_RED to "白红").forEach { (t, label) ->
                    if (t == theme) Button(onClick = { vm.setTheme(t) }) { Text(label) }
                    else OutlinedButton(onClick = { vm.setTheme(t) }) { Text(label) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("局域网同步", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { vm.startDiscovery() }) { Text("扫描 Mac") }
                Button(onClick = { vm.sync(vm.manualBaseUrl()) }, enabled = !s.syncing && s.manualHost.isNotBlank()) { Text("手填同步") }
            }
            OutlinedTextField(
                value = s.manualHost, onValueChange = { vm.setManualHost(it) },
                label = { Text("手动 IP(如 192.168.5.139)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            s.servers.forEach { server ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${server.name} — ${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.sync(server.baseUrl) }, enabled = !s.syncing) { Text("同步") }
                }
            }
            s.progress?.let { Text(progressText(it), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("关于", style = MaterialTheme.typography.titleSmall)
            Text("TingMusic v0.1.0", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
