package com.tingmusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tingmusic.library.LibraryIndexer
import com.tingmusic.library.Track
import com.tingmusic.sync.DiscoveredServer
import com.tingmusic.sync.SyncClient
import com.tingmusic.sync.SyncEngine
import com.tingmusic.sync.SyncProgress
import com.tingmusic.sync.SyncStateStore
import com.tingmusic.sync.discoverServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val servers: List<DiscoveredServer> = emptyList(),
    val manualHost: String = "",
    val progress: SyncProgress? = null,
    val tracks: List<Track> = emptyList(),
    val syncing: Boolean = false,
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val mirrorRoot: File = app.getExternalFilesDir("Music") ?: app.filesDir
    private val stateStore = SyncStateStore(File(app.filesDir, "sync_state.json"))
    private val client = SyncClient()
    private val engine = SyncEngine(client, stateStore, mirrorRoot)
    private val indexer = LibraryIndexer(mirrorRoot)

    private val settings = com.tingmusic.data.SettingsStore(app)
    val theme: kotlinx.coroutines.flow.StateFlow<com.tingmusic.ui.theme.AppTheme> = settings.theme
    fun setTheme(t: com.tingmusic.ui.theme.AppTheme) = settings.setTheme(t)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var discoveryJob: Job? = null

    init { viewModelScope.launch { refreshLibrary() } }

    fun setManualHost(host: String) { _state.value = _state.value.copy(manualHost = host) }

    fun startDiscovery() {
        discoveryJob?.cancel()
        _state.value = _state.value.copy(servers = emptyList())
        discoveryJob = viewModelScope.launch {
            discoverServers(getApplication()).collect { server ->
                val cur = _state.value.servers
                if (cur.none { it.baseUrl == server.baseUrl }) {
                    _state.value = _state.value.copy(servers = cur + server)
                }
            }
        }
    }

    /** baseUrl 来自发现的服务器,或手填 host(补全成 http://host:8737)。 */
    fun sync(baseUrl: String) {
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, progress = SyncProgress.FetchingManifest)
        viewModelScope.launch {
            engine.sync(baseUrl) { p ->
                _state.value = _state.value.copy(progress = p)
            }
            refreshLibrary()                                  // suspends until re-indexed
            _state.value = _state.value.copy(syncing = false) // clear only after the list is updated
        }
    }

    fun manualBaseUrl(): String {
        val h = _state.value.manualHost.trim()
        return if (h.startsWith("http")) h else "http://$h:8737"
    }

    private suspend fun refreshLibrary() {
        val tracks = withContext(Dispatchers.IO) { indexer.index() }
        _state.value = _state.value.copy(tracks = tracks)
    }
}
