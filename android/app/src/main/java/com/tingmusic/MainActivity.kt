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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import com.tingmusic.playback.PlaybackController
import com.tingmusic.ui.DrawerContent
import com.tingmusic.ui.MiniPlayer
import com.tingmusic.ui.NowPlayingScreen
import com.tingmusic.ui.PlaylistScreen
import com.tingmusic.ui.SyncViewModel
import com.tingmusic.ui.theme.TingMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()
    private lateinit var playback: PlaybackController

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(applicationContext)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            TingMusicTheme(theme) {
                val ui by vm.state.collectAsStateWithLifecycle()
                val pstate by playback.state.collectAsState()
                var showNowPlaying by remember { mutableStateOf(false) }
                var livePos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(pstate.isPlaying, pstate.currentId) {
                    while (true) { livePos = playback.currentPositionMs(); delay(300) }
                }
                LaunchedEffect(ui.tracks) { playback.setLibrary(ui.tracks) }

                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val current = ui.tracks.find { it.id == pstate.currentId }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = { DrawerContent(vm) },
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("TingMusic") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, "菜单")
                                    }
                                },
                            )
                        },
                        bottomBar = {
                            if (current != null) {
                                MiniPlayer(
                                    track = current, isPlaying = pstate.isPlaying,
                                    onToggle = { playback.togglePlayPause() },
                                    onOpen = { showNowPlaying = true },
                                )
                            }
                        },
                    ) { inner ->
                        PlaylistScreen(
                            tracks = ui.tracks, currentId = pstate.currentId,
                            onPlay = { playback.play(it); showNowPlaying = true },
                            modifier = Modifier.padding(inner),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showNowPlaying && current != null,
                    enter = slideInVertically { it }, exit = slideOutVertically { it },
                ) {
                    current?.let { c ->
                        NowPlayingScreen(
                            track = c, state = pstate, livePositionMs = livePos,
                            onClose = { showNowPlaying = false },
                            onToggle = { playback.togglePlayPause() },
                            onNext = { playback.next() }, onPrev = { playback.prev() },
                            onSeek = { playback.seekTo(it) }, onCycleMode = { playback.cycleMode() },
                        )
                    }
                }
                BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
            }
        }
    }

    override fun onStart() { super.onStart(); playback.connect() }
    override fun onStop() { super.onStop(); playback.release() }
}
