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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState(player)
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
}
