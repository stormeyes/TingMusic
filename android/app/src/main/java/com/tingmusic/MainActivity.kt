package com.tingmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.DrawerContent
import com.tingmusic.ui.LyricsScreen
import com.tingmusic.ui.MiniPlayer
import com.tingmusic.ui.PlayerScreen
import com.tingmusic.ui.PlaylistScreen
import com.tingmusic.ui.SyncViewModel
import com.tingmusic.ui.theme.TingMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            TingMusicTheme {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                var screen by remember { mutableStateOf("list") } // list | player | lyrics
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val current = ui.tracks.find { it.id == pstate.currentId }
                val dur = if (pstate.durationMs > 0) pstate.durationMs else (current?.durationMs ?: 0L)
                val progress = if (dur > 0) (livePos.toFloat() / dur) else 0f

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = { DrawerContent(vm, onClose = { scope.launch { drawerState.close() } }) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (current != null) {
                                MiniPlayer(
                                    track = current, isPlaying = pstate.isPlaying, progress = progress,
                                    onToggle = { playback.togglePlayPause() },
                                    onOpen = { screen = "player" },
                                )
                            }
                        },
                    ) { inner ->
                        PlaylistScreen(
                            tracks = ui.tracks, currentId = pstate.currentId, isPlaying = pstate.isPlaying,
                            onPlayTrack = { playback.play(it); screen = "player" },
                            onPlayAll = { ui.tracks.firstOrNull()?.let { playback.play(it); screen = "player" } },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            modifier = Modifier.padding(inner),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = screen != "list" && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        PlayerScreen(
                            track = c, state = pstate, livePositionMs = livePos,
                            onClose = { screen = "list" },
                            onOpenLyrics = { screen = "lyrics" },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                            onSeek = { playback.seekTo(it) }, onCycleMode = { playback.cycleMode() },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = screen == "lyrics" && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        LyricsScreen(
                            track = c, livePositionMs = livePos, isPlaying = pstate.isPlaying,
                            onBack = { screen = "player" },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                        )
                    }
                }
                BackHandler(enabled = screen != "list") {
                    screen = if (screen == "lyrics") "player" else "list"
                }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
