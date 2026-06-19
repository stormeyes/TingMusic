package com.tingmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.sync.SyncProgress
import com.tingmusic.ui.theme.RB

@Composable
fun DrawerContent(vm: SyncViewModel, onClose: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf("menu") }

    ModalDrawerSheet(drawerContainerColor = RB.DrawerBg) {
        if (view == "menu") {
            MenuView(
                s = s,
                vm = vm,
                onAbout = { view = "about" },
                onClose = onClose,
            )
        } else {
            AboutView(onBack = { view = "menu" })
        }
    }
}

@Composable
private fun MenuView(
    s: UiState,
    vm: SyncViewModel,
    onAbout: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 头部
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(RB.Red),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("TingMusic", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RB.Text)
                Text("局域网音乐播放器 · 你的私人电台", fontSize = 11.5.sp, color = RB.TextDim)
            }
        }
        HorizontalDivider(color = RB.Divider)

        // 局域网同步
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("局域网同步", fontSize = 12.sp, color = RB.TextDim, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.startDiscovery() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RB.Text),
                ) { Text("扫描 Mac") }
                Button(
                    onClick = { vm.sync(vm.manualBaseUrl()) },
                    enabled = !s.syncing && s.manualHost.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RB.Red, contentColor = Color.White),
                ) { Text("手填同步") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.manualHost,
                onValueChange = { vm.setManualHost(it) },
                label = { Text("手动 IP(如 192.168.5.139)", color = RB.TextDim, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RB.Text,
                    unfocusedTextColor = RB.Text,
                    focusedBorderColor = RB.Red,
                    unfocusedBorderColor = RB.Divider,
                    cursorColor = RB.Red,
                ),
            )

            // 发现的服务器
            s.servers.forEach { server ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${server.name} — ${server.host}:${server.port}",
                        fontSize = 12.sp, color = RB.TextDim,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.sync(server.baseUrl) },
                        enabled = !s.syncing,
                        colors = ButtonDefaults.buttonColors(containerColor = RB.Red, contentColor = Color.White),
                    ) { Text("同步") }
                }
            }

            // 进度文案
            s.progress?.let {
                Spacer(Modifier.height(6.dp))
                Text(progressText(it), fontSize = 12.sp, color = RB.TextDim)
            }
        }

        HorizontalDivider(color = RB.Divider)

        // 关于行
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onAbout() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, null, tint = RB.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("关于 TingMusic", fontSize = 15.sp, color = RB.Text, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = RB.TextDim, modifier = Modifier.size(20.dp))
        }

        HorizontalDivider(color = RB.Divider)

        Spacer(Modifier.weight(1f))

        // 版权
        Text(
            "© 2026 TingMusic · v0.1.0",
            fontSize = 11.sp, color = RB.TextWeak,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun AboutView(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 返回
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "返回", tint = RB.Text)
            }
            Text("关于", fontSize = 15.sp, color = RB.Text, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(40.dp))

        // 红 logo 74dp 圆角20
        Box(
            Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RB.Red),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("TingMusic", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RB.Text)
        Spacer(Modifier.height(6.dp))
        Text("版本 0.1.0", fontSize = 12.sp, color = RB.TextDim)
        Spacer(Modifier.height(8.dp))
        Text("本地局域网同步音乐播放器", fontSize = 13.sp, color = RB.TextDim)

        Spacer(Modifier.weight(1f))

        Text(
            "© 2026 · Made with 红 & 黑",
            fontSize = 11.sp, color = RB.TextWeak,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
