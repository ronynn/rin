package io.github.ronynn.rin

import android.app.Application
import android.content.ComponentName
import android.net.Uri
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
    var coverVersion by mutableIntStateOf(0)
    var isScanning by mutableStateOf(false)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val prefs get() = getApplication<Application>().getSharedPreferences("rin", 0)

    init { themeIndex = prefs.getInt("theme", 0) }

    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    private fun hiddenIds(): MutableSet<Long> =
        prefs.getStringSet("hidden", emptySet())!!
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()

    private fun saveHidden(ids: Set<Long>) {
        prefs.edit().putStringSet("hidden", ids.map { it.toString() }.toSet()).apply()
    }

    fun connect() {
        if (controller != null) return
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlayerService::class.java))
        controllerFuture = MediaController.Builder(ctx, token).buildAsync().also { f ->
            f.addListener({
                val c = runCatching { f.get() }.getOrNull() ?: return@addListener
                controller = c
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

    /** Normal scan — respects the persisted hidden set. */
    fun scanDevice() = viewModelScope.launch {
        if (isScanning) return@launch
        isScanning = true
        status = "SCANNING…"
        val found = withContext(Dispatchers.IO) { MediaLibrary.scan(getApplication()) }
        val hidden = hiddenIds()
        val visible = found.filterNot { it.id in hidden }
        Snapshot.withMutableSnapshot {
            tracks.clear()
            tracks.addAll(visible)
        }
        status = if (visible.isEmpty()) "NO MUSIC FOUND" else ""
        if (controller != null && tracks.isNotEmpty() && currentIndex < 0) setQueue(0)
        isScanning = false
    }

    /** RESCAN button — clears the hidden set first so deleted tracks return. */
    fun rescan() = viewModelScope.launch {
        if (isScanning) return@launch
        isScanning = true
        status = "RESCANNING…"
        saveHidden(emptySet())
        val found = withContext(Dispatchers.IO) { MediaLibrary.scan(getApplication()) }
        Snapshot.withMutableSnapshot {
            tracks.clear()
            tracks.addAll(found)
        }
        status = if (found.isEmpty()) "NO MUSIC FOUND" else ""
        if (controller != null && tracks.isNotEmpty() && currentIndex < 0) setQueue(0)
        isScanning = false
    }

    fun playExternalUri(uri: Uri) = viewModelScope.launch {
        val t = withContext(Dispatchers.IO) {
            MediaLibrary.fromUri(getApplication(), uri)
        } ?: return@launch
        tracks.add(0, t)
        setQueue(0)
        controller?.play()
    }

    fun playExternalUris(uris: List<Uri>) { uris.forEach { playExternalUri(it) } }

    private fun setQueue(startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) { c.clearMediaItems(); currentIndex = -1; return }
        val items = tracks.map { track ->
            val artwork = coverFile(track).takeIf { it.exists() }
                ?.let { Uri.fromFile(it) } ?: track.albumArtUri
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(artwork)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.prepare()
        currentIndex = startIndex
    }

    fun playAt(i: Int) {
        val c = controller ?: return
        if (i !in tracks.indices) return
        if (c.mediaItemCount == 0) setQueue(i) else c.seekToDefaultPosition(i)
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next()     { controller?.let { it.seekToNextMediaItem(); it.play() } }
    fun previous() { controller?.let { it.seekToPreviousMediaItem(); it.play() } }
    fun seekToFraction(f: Float) { controller?.let { it.seekTo((it.duration * f).toLong()) } }

    fun removeAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val hidden = hiddenIds()
        hidden.add(track.id)          // persists; survives restart
        saveHidden(hidden)

        val wasCurrent = index == currentIndex
        tracks.removeAt(index)

        if (tracks.isEmpty()) {
            controller?.clearMediaItems()
            currentIndex = -1
        } else {
            val newIndex = if (wasCurrent) index.coerceAtMost(tracks.size - 1)
                           else if (index < currentIndex) currentIndex - 1
                           else currentIndex
            setQueue(newIndex)
            if (!wasCurrent && controller?.isPlaying == true) controller?.play()
        }
    }

    fun setTheme(i: Int) {
        themeIndex = i
        prefs.edit().putInt("theme", i).apply()
    }

    private fun startTicker() = viewModelScope.launch {
        while (isActive) {
            controller?.let {
                positionMs = it.currentPosition.coerceAtLeast(0)
                durationMs = it.duration.coerceAtLeast(0)
                progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
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
        File(getApplication<Application>().filesDir,
            "covers/${track.stableKey.hashCode()}.img")

    fun coverModel(track: Track, version: Int): Any {
        val f = coverFile(track)
        return if (f.exists())
            Uri.fromFile(f).buildUpon()
                .appendQueryParameter("v", version.toString()).build()
        else track.albumArtUri
    }

    fun setCustomCover(uri: Uri) {
        val track = currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val f = coverFile(track)
                f.parentFile?.mkdirs()
                getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    }
            }
            withContext(Dispatchers.Main) {
                coverVersion++
                val idx = currentIndex
                if (idx >= 0) setQueue(idx)   // rebuild so notification art updates
                controller?.play()
            }
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}