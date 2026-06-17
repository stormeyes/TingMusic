package com.tingmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.SyncScreen
import com.tingmusic.ui.SyncViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                // 每 300ms 拉一次真实进度(MediaController.position 只能主线程读)
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                // 曲库变化时同步给 controller(用于 play 队列)
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    SyncScreen(
                        vm = vm,
                        playback = pstate,
                        onPlayTrack = { playback.play(it) },
                        onToggle = { playback.togglePlayPause() },
                        onNext = { playback.next() },
                        onPrev = { playback.prev() },
                        onSeek = { playback.seekTo(it) },
                        onCycleMode = { playback.cycleMode() },
                        livePositionMs = livePos,
                        modifier = Modifier.padding(inner),
                    )
                }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
