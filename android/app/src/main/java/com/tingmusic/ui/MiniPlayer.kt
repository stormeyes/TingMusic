package com.tingmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingmusic.library.Track
import com.tingmusic.ui.theme.RB

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(RB.MiniBg)) {
        HorizontalDivider(color = RB.Divider)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面(方角裁剪外层)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onOpen() },
            ) {
                CoverImage(track = track, isPlaying = false, sizeDp = 42)
            }

            // 标题 + 艺术家
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clickable { onOpen() },
            ) {
                Text(
                    track.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = RB.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    fontSize = 11.sp,
                    color = RB.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // conic 进度环播放按钮
            ConicPlayButton(progress = progress, isPlaying = isPlaying, onClick = onToggle, sizeDp = 38)

            Spacer(Modifier.width(8.dp))

            // 队列图标(装饰)
            Icon(
                Icons.Filled.PlaylistPlay, null,
                tint = RB.TextDim, modifier = Modifier.size(21.dp),
            )
        }
    }
}
