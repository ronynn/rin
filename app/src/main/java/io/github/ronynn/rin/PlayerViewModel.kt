package io.github.ronynn.rin

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val tracks: SnapshotStateList<Track> = mutableListOf<Track>().toMutableStateList()
    var currentIndex by mutableIntStateOf(-1)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var progress by mutableStateOf(0f)
    var status by mutableStateOf("")

    var themeIndex by mutableIntStateOf(0)
    var fontIndex by mutableIntStateOf(0)
    var coverVersion by mutableIntStateOf(0)
    var dynamicColors by mutableStateOf<RinColors?>(null)

    var isScanning by mutableStateOf(false)
    var hasScanned by mutableStateOf(false)

    var viewTab by mutableStateOf("all")
    var searchQuery by mutableStateOf("")
    var isSearchOpen by mutableStateOf(false)

    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf("off")

    var favorites by mutableStateOf<Set<String>>(emptySet())
    var historyIds by mutableStateOf<List<String>>(emptyList())
    var openFolders by mutableStateOf<Set<String>>(emptySet())

    var editingTrack by mutableStateOf<Track?>(null)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val prefs get() = getApplication<Application>().getSharedPreferences("rin", 0)

    init {
        themeIndex = prefs.getInt("theme", 0)
        fontIndex = prefs.getInt("font", 0)
        shuffle = prefs.getBoolean("shuffle", false)
        repeatMode = prefs.getString("repeat", "off") ?: "off"
        favorites = prefs.getStringSet("favorites", emptySet())!!.toSet()
        openFolders = prefs.getStringSet("openFolders", emptySet())!!.toSet()
        historyIds = prefs.getString("history", "")?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    val groupedTracks by derivedStateOf {
        val map = sortedMapOf<String, MutableList<Track>>(compareBy { it.lowercase() })
        tracks.forEach { map.getOrPut(it.folder) { mutableListOf() }.add(it) }
        map
    }

    val favoriteTracks by derivedStateOf {
        tracks.filter { it.uniqueKey in favorites }
    }

    val historyTracks by derivedStateOf {
        if (historyIds.isEmpty()) emptyList()
        else {
            val byKey = tracks.associateBy { it.uniqueKey }
            historyIds.mapNotNull { byKey[it] }
        }
    }

    val searchResults by derivedStateOf {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folder.lowercase().contains(q)
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlayerService::class.java))
        controllerFuture = MediaController.Builder(ctx, token).buildAsync().also { f ->
            f.addListener({
                val c = runCatching { f.get() }.getOrNull() ?: return@addListener
                controller = c
                c.shuffleModeEnabled = shuffle
                c.repeatMode = when (repeatMode) {
                    "all" -> Player.REPEAT_MODE_ALL
                    "one" -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        currentIndex = c.currentMediaItemIndex
                    }
                })
                startTicker()
            }, ContextCompat.getMainExecutor(ctx))
        }
    }

    fun scanDevice() = viewModelScope.launch {
        if (isScanning) return@launch
        isScanning = true
        status = "SCANNING…"
        val found = withContext(Dispatchers.IO) { MediaLibrary.scan(getApplication()) }
        val overridden = found.map { applyMetadataOverride(it) }
        Snapshot.withMutableSnapshot {
            tracks.clear()
            tracks.addAll(overridden)
        }
        status = if (overridden.isEmpty()) "NO MUSIC FOUND" else ""
        if (controller != null && tracks.isNotEmpty() && currentIndex < 0) {
            val lastIndex = prefs.getInt("lastIndex", 0).coerceIn(0, tracks.size - 1)
            val lastPos = prefs.getLong("lastPos", 0L)
            setQueue(lastIndex, lastPos)
            openFolders = openFolders + (tracks[lastIndex].folder)
            saveOpenFolders()
        }
        isScanning = false
        hasScanned = true
    }

    fun rescan() = viewModelScope.launch {
        if (isScanning) return@launch
        isScanning = true
        status = "RESCANNING…"
        val found = withContext(Dispatchers.IO) { MediaLibrary.scan(getApplication()) }
        val overridden = found.map { applyMetadataOverride(it) }
        Snapshot.withMutableSnapshot {
            tracks.clear()
            tracks.addAll(overridden)
        }
        status = if (overridden.isEmpty()) "NO MUSIC FOUND" else ""
        isScanning = false
        hasScanned = true
    }

    fun playExternalUri(uri: Uri) = viewModelScope.launch {
        val t = withContext(Dispatchers.IO) { MediaLibrary.fromUri(getApplication(), uri) } ?: return@launch
        tracks.add(0, t)
        setQueue(0, 0L)
        controller?.play()
    }

    fun playExternalUris(uris: List<Uri>) { uris.forEach { playExternalUri(it) } }

    private fun setQueue(startIndex: Int, startPos: Long) {
        val c = controller ?: return
        if (tracks.isEmpty()) { c.clearMediaItems(); currentIndex = -1; return }
        val items = tracks.map { track ->
            val customCover = coverFile(track).takeIf { it.exists() }?.let { Uri.fromFile(it) }
            val artworkUri = customCover ?: track.albumArtUri.takeIf { track.id > 0 }
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), startPos)
        c.prepare()
        currentIndex = startIndex
    }

    fun playTrack(track: Track) {
        val idx = tracks.indexOfFirst { it.uri == track.uri }
        if (idx < 0) return
        if (controller?.mediaItemCount == 0) setQueue(idx, 0L)
        else controller?.seekToDefaultPosition(idx)
        controller?.play()
        addToHistory(track.uniqueKey)
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next()     { controller?.let { it.seekToNextMediaItem(); it.play() } }
    fun previous() { controller?.let { it.seekToPreviousMediaItem(); it.play() } }
    fun seekToFraction(f: Float) { controller?.let { it.seekTo((it.duration * f).toLong()) } }

    fun toggleShuffle() {
        shuffle = !shuffle
        controller?.shuffleModeEnabled = shuffle
        prefs.edit().putBoolean("shuffle", shuffle).apply()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            "off" -> "all"
            "all" -> "one"
            else -> "off"
        }
        controller?.repeatMode = when (repeatMode) {
            "all" -> Player.REPEAT_MODE_ALL
            "one" -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        prefs.edit().putString("repeat", repeatMode).apply()
    }

    fun toggleFav(track: Track) {
        val key = track.uniqueKey
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    fun isFavorite(track: Track): Boolean = track.uniqueKey in favorites

    fun selectView(tab: String) {
        viewTab = tab
        searchQuery = ""
        isSearchOpen = false
    }

    fun updateSearch(q: String) { searchQuery = q }

    fun toggleSearch() {
        isSearchOpen = !isSearchOpen
        if (!isSearchOpen) searchQuery = ""
    }

    fun toggleFolder(folder: String) {
        openFolders = if (folder in openFolders) openFolders - folder else openFolders + folder
        saveOpenFolders()
    }

    private fun saveOpenFolders() {
        prefs.edit().putStringSet("openFolders", openFolders).apply()
    }

    private fun addToHistory(key: String) {
        val updated = (listOf(key) + historyIds.filter { it != key }).take(50)
        historyIds = updated
        prefs.edit().putString("history", updated.joinToString("\n")).apply()
    }

    fun applyTheme(i: Int) {
        themeIndex = i
        prefs.edit().putInt("theme", i).apply()
    }

    fun applyFont(i: Int) {
        fontIndex = i
        prefs.edit().putInt("font", i).apply()
    }

    fun startEditing(track: Track) { editingTrack = track }
    fun cancelEditing() { editingTrack = null }

    fun saveMetadata(title: String, artist: String, album: String) {
        val track = editingTrack ?: return
        val idx = tracks.indexOfFirst { it.uri == track.uri }
        if (idx < 0) { editingTrack = null; return }
        val updated = tracks[idx].copy(title = title, artist = artist, album = album)
        tracks[idx] = updated
        prefs.edit().putString("meta_${track.uniqueKey}", "$title\u0001$artist\u0001$album").apply()
        if (currentIndex == idx) {
            val pos = controller?.currentPosition ?: 0L
            setQueue(currentIndex, pos)
        }
        editingTrack = null
    }

    private fun applyMetadataOverride(track: Track): Track {
        val s = prefs.getString("meta_${track.uniqueKey}", null) ?: return track
        val parts = s.split('\u0001')
        if (parts.size < 3) return track
        return track.copy(title = parts[0], artist = parts[1], album = parts[2])
    }

    private fun startTicker() = viewModelScope.launch {
        while (isActive) {
            controller?.let {
                positionMs = it.currentPosition.coerceAtLeast(0)
                durationMs = it.duration.coerceAtLeast(0)
                progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                prefs.edit()
                    .putInt("lastIndex", currentIndex)
                    .putLong("lastPos", positionMs)
                    .apply()
            }
            delay(200)
        }
    }

    fun format(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun coverFile(track: Track): File =
        File(getApplication<Application>().filesDir, "covers/${track.uniqueKey.hashCode()}.img")

    fun coverModel(track: Track, version: Int): Any {
        val f = coverFile(track)
        return if (f.exists()) Uri.fromFile(f).buildUpon().appendQueryParameter("v", "$version").build()
        else track.albumArtUri
    }

    fun setCustomCover(uri: Uri) {
        val track = currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val f = coverFile(track)
                f.parentFile?.mkdirs()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                }
            }
            withContext(Dispatchers.Main) {
                coverVersion++
                if (currentIndex >= 0) setQueue(currentIndex, controller?.currentPosition ?: 0L)
                controller?.play()
            }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}