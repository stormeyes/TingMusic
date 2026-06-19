package com.tingmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingmusic.library.Track
import com.tingmusic.ui.theme.RB

@Composable
fun PlaylistScreen(
    tracks: List<Track>,
    currentId: String?,
    isPlaying: Boolean,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(RB.Bg)) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, "菜单", tint = RB.Text, modifier = Modifier.size(22.dp))
            }
            // 搜索框(装饰)
            Row(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RB.SearchBg)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = RB.TextDim, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("搜索歌曲、歌手、专辑", fontSize = 13.sp, color = RB.TextDim)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.MoreVert, null, tint = RB.Text, modifier = Modifier.size(22.dp))
        }

        // 标题块
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp)) {
            Text("我的曲库", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RB.Text)
            Text("本地曲库 · ${tracks.size} 首", fontSize = 12.sp, color = RB.TextDim)
        }

        // 播放全部行
        if (tracks.isNotEmpty()) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clickable { onPlayAll() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RedCirclePlay(24)
                        Spacer(Modifier.width(9.dp))
                        Text("播放全部", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RB.Text)
                        Spacer(Modifier.width(6.dp))
                        Text("(${tracks.size})", fontSize = 13.sp, color = RB.TextWeak)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.Sort, null,
                        tint = RB.TextDim, modifier = Modifier.size(20.dp),
                    )
                }
                HorizontalDivider(color = RB.Divider)
            }
        }

        if (tracks.isEmpty()) {
            // 空库引导文案
            Column(
                Modifier.fillMaxSize().padding(24.dp),
            ) {
                Text("曲库是空的", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = RB.Text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "打开左上角抽屉,扫描局域网内的 Mac 同步曲库。",
                    fontSize = 14.sp, color = RB.TextDim,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tracks, key = { it.id }) { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 62.dp)
                            .clickable { onPlayTrack(t) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 左:封面缩略图(当前播放叠加均衡器)
                        TrackThumb(track = t, isCurrent = t.id == currentId, isPlaying = isPlaying)
                        // 中:标题 + 艺术家
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                t.title,
                                fontSize = 15.sp,
                                color = if (t.id == currentId) RB.Red else Color(0xFFF3F3F3),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t.artist,
                                fontSize = 12.sp,
                                color = RB.TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 右:装饰 ⋮
                        Icon(
                            Icons.Filled.MoreVert, null,
                            tint = RB.TextWeak, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 列表行缩略图:圆角方形封面;当前正在播放时压暗并叠加均衡器。 */
@Composable
private fun TrackThumb(track: Track, isCurrent: Boolean, isPlaying: Boolean) {
    val cover = rememberCover(track)
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RB.SearchBg),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                cover, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 无封面占位:小红点
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(RB.Red))
        }
        if (isCurrent && isPlaying) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { EqualizerBars() }
        }
    }
}
