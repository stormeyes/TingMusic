package com.tingmusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tingmusic.library.Track
import com.tingmusic.sync.CoverFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI 观察的播放状态。currentId 为当前曲目 id(Track.id = 相对路径)。 */
data class PlaybackState(
    val currentId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val mode: PlayMode = PlayMode.SEQUENTIAL,
)

/**
 * 连接 PlaybackService 的 MediaController 封装。connect() 在 Activity onStart 调用,
 * release() 在 onStop。命令直接转发给 controller。播放时设置整个曲库为队列。
 */
class PlaybackController(private val context: Context) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** 当前曲库快照(用于 play(index) 与 id 映射)。 */
    private var tracks: List<Track> = emptyList()

    /** 异步回填封面用;随 controller 生命周期长存,协程内自查 controller 是否仍在。 */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val coverFetcher by lazy { CoverFetcher(context) }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState(player)
            // 曲目切换 / 队列变化时,给当前曲目补封面(锁屏/通知靠 artwork 显示 + 配色)。
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                applyArtworkFor(player.currentMediaItemIndex)
            }
        }
    }

    fun connect() {
        if (controller != null) return // guard against double-connect leaking a controller
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            pushState(c)
        }, context.mainExecutor)
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun setLibrary(list: List<Track>) { tracks = list }

    /** 用整个曲库做队列,从 track 处开始播。 */
    fun play(track: Track) {
        val c = controller ?: return
        val idx = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(Uri.fromFile(t.file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setAlbumTitle(t.album)
                        .build(),
                )
                .build()
        }
        c.setMediaItems(items, idx, 0)
        applyMode(c, _state.value.mode)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(posMs: Long) { controller?.seekTo(posMs) }

    fun cycleMode() {
        val c = controller ?: return
        val nextMode = _state.value.mode.next()
        applyMode(c, nextMode)
        _state.value = _state.value.copy(mode = nextMode)
    }

    /** 供 UI 轮询当前进度(MediaController 的 position 只能主线程读)。 */
    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private fun applyMode(c: MediaController, mode: PlayMode) {
        c.repeatMode = mode.repeatMode
        c.shuffleModeEnabled = mode.shuffle
    }

    private fun pushState(player: Player) {
        _state.value = _state.value.copy(
            currentId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
        )
    }

    /**
     * 给指定位置的曲目异步补封面字节并写回 MediaMetadata。队列里只带了标题/歌手,
     * 封面较慢(内嵌解码 / iTunes 联网)所以播放后才回填:用相同 URI 的 replaceMediaItem
     * 只更新 metadata,不打断播放(Media3 canUpdateMediaItem)。已有 artwork 则跳过。
     */
    private fun applyArtworkFor(index: Int) {
        val c = controller ?: return
        if (index < 0 || index >= c.mediaItemCount) return
        val item = c.getMediaItemAt(index)
        if (item.mediaMetadata.artworkData != null) return
        val id = item.mediaId
        val track = tracks.firstOrNull { it.id == id } ?: return
        scope.launch {
            val bytes = coverFetcher.coverBytes(track) ?: return@launch
            val cc = controller ?: return@launch
            // 队列可能已变,用 mediaId 重新定位;期间若已被别的回填补上则跳过。
            val curIdx = (0 until cc.mediaItemCount)
                .firstOrNull { cc.getMediaItemAt(it).mediaId == id } ?: return@launch
            val cur = cc.getMediaItemAt(curIdx)
            if (cur.mediaMetadata.artworkData != null) return@launch
            val updated = cur.buildUpon()
                .setMediaMetadata(
                    cur.mediaMetadata.buildUpon()
                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build(),
                )
                .build()
            cc.replaceMediaItem(curIdx, updated)
        }
    }
}
