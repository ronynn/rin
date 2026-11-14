package io.github.ronynn.rin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object BitmapCache {
    private val map = HashMap<String, Bitmap>()
    fun get(k: String): Bitmap? = map[k]
    fun put(k: String, b: Bitmap) { if (map.size > 20) map.clear(); map[k] = b }
}

@Composable
private fun RinImage(model: Any?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(model) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(model) {
        val uri = model as? Uri ?: return@LaunchedEffect
        val key = uri.toString()
        BitmapCache.get(key)?.let { bmp = it; return@LaunchedEffect }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        bmp?.let { BitmapCache.put(key, it) }
    }
    bmp?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop) }
}

@Composable
fun Fieldset(
    legend: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalRinColors.current
    val shape = if (c.isGlass) RoundedCornerShape(12.dp) else RectangleShape
    val borderWidth = if (c.isGlass) 1.dp else 2.dp

    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(top = if (c.isGlass) 9.dp else 7.dp)
                .then(if (c.isGlass) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
                .border(borderWidth, c.border, shape)
                .background(c.panel, shape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
        if (!c.isGlass) {
            Text(
                legend.uppercase(),
                Modifier.padding(start = 14.dp).background(c.panel).padding(horizontal = 6.dp),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = c.accent
                )
            )
        }
    }
}

@Composable
fun RinButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalRinColors.current
    val shape = if (c.isGlass) RoundedCornerShape(8.dp) else RectangleShape
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        border = BorderStroke(if (c.isGlass) 1.dp else 2.dp, c.border),
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) c.fg else Color.Transparent,
            contentColor = if (selected) c.bg else c.fg
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val c = LocalRinColors.current

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let { vm.setCustomCover(it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) vm.playExternalUris(it)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .drawBehind {
                drawRect(c.bg)
                c.blobs.forEach { blob ->
                    val center = Offset(size.width * blob.cx, size.height * blob.cy)
                    val radius = size.minDimension * blob.r
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(blob.color, Color.Transparent),
                            center = center,
                            radius = radius
                        )
                    )
                }
            }
            .safeDrawingPadding()
    ) {
        val twoCol = maxWidth >= 800.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val coverSize = when { maxHeight <= 560.dp -> 72.dp; maxHeight <= 720.dp -> 96.dp; else -> 124.dp }
        val visHeight = when { maxHeight <= 560.dp -> 56.dp; maxHeight <= 720.dp -> 80.dp; else -> 120.dp }

        if (twoCol) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsPanel(vm, onOpenFile = { filePicker.launch(arrayOf("audio/*", "application/ogg")) })
                    PlaylistPanel(vm, Modifier.weight(1f))
                }
                PlayerPanel(
                    vm, coverSize, visHeight, landscape = true,
                    onCoverClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsPanel(vm, onOpenFile = { filePicker.launch(arrayOf("audio/*", "application/ogg")) })
                PlayerPanel(
                    vm, coverSize, visHeight, landscape = false,
                    onCoverClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                PlaylistPanel(vm, Modifier.weight(1f))
            }
        }

        vm.editingTrack?.let { track ->
            EditMetadataDialog(
                track,
                onSave = { t, a, al -> vm.saveMetadata(t, a, al) },
                onCancel = { vm.cancelEditing() }
            )
        }
    }
}

@Composable
private fun SettingsPanel(vm: PlayerViewModel, onOpenFile: () -> Unit) {
    val c = LocalRinColors.current
    var themeMenu by remember { mutableStateOf(false) }
    var fontMenu by remember { mutableStateOf(false) }

    Fieldset("./Settings/") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RinButton(if (vm.isScanning) "SCAN…" else "RESCAN", Modifier.weight(1f)) { vm.rescan() }

            Box(Modifier.weight(1f)) {
                RinButton(Themes[vm.themeIndex].first.uppercase(), Modifier.fillMaxWidth()) { themeMenu = true }
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    Themes.forEachIndexed { i, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name.uppercase(), style = TextStyle(fontSize = 12.sp)) },
                            onClick = { vm.setTheme(i); themeMenu = false }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                RinButton(Fonts[vm.fontIndex].first.uppercase(), Modifier.fillMaxWidth()) { fontMenu = true }
                DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                    Fonts.forEachIndexed { i, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name.uppercase(), style = TextStyle(fontSize = 12.sp)) },
                            onClick = { vm.setFont(i); fontMenu = false }
                        )
                    }
                }
            }
        }
        if (vm.status.isNotEmpty()) {
            Text(
                vm.status,
                style = TextStyle(fontSize = 10.sp, color = c.accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerPanel(
    vm: PlayerViewModel,
    coverSize: Dp,
    visHeight: Dp,
    landscape: Boolean,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRinColors.current
    val track = vm.currentTrack
    val wave = PlayerService.waveform.collectAsState()

    Fieldset("./Player/", modifier, fillHeight = landscape) {
        Row(Modifier.fillMaxWidth().height(coverSize).border(2.dp, c.border).background(c.bg)) {
            Box(
                Modifier.size(coverSize - 4.dp).border(2.dp, c.border).clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                if (track != null) {
                    val v = vm.coverVersion
                    RinImage(remember(v, track) { vm.coverModel(track, v) }, Modifier.fillMaxSize())
                } else {
                    Text("♪", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, color = c.accent))
                }
            }
            Column(
                Modifier.weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .pointerInput(track) {
                        detectTapGestures(onDoubleTap = { if (track != null) vm.startEditing(track) })
                    },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    track?.title ?: "NO TRACK LOADED",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.fg, lineHeight = 17.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track?.artist ?: "UNKNOWN ARTIST",
                    style = TextStyle(fontSize = 11.5.sp, color = c.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.album ?: "UNKNOWN ALBUM",
                    style = TextStyle(fontSize = 11.5.sp, color = c.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (landscape) RinVisualizer(wave, vm.isPlaying, Modifier.fillMaxWidth().weight(1f))
        else RinVisualizer(wave, vm.isPlaying, Modifier.fillMaxWidth().height(visHeight))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn("⇆", active = vm.shuffle) { vm.toggleShuffle() }
            Spacer(Modifier.width(12.dp))
            TransportButton("◀◀") { vm.previous() }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(42.dp).border(2.dp, c.border, CircleShape).background(c.bg)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (vm.isPlaying) "❚❚" else "▶",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.fg)
                )
            }
            Spacer(Modifier.width(12.dp))
            TransportButton("▶▶") { vm.next() }
            Spacer(Modifier.width(12.dp))
            IconBtn(if (vm.repeatMode == "one") "↻1" else "↻", active = vm.repeatMode != "off") { vm.cycleRepeat() }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(vm.format(vm.positionMs), style = TextStyle(fontSize = 10.sp, color = c.fg))
            ProgressBar(vm.progress, Modifier.weight(1f)) { vm.seekToFraction(it) }
            Text(vm.format(vm.durationMs), style = TextStyle(fontSize = 10.sp, color = c.fg))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IconBtn(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalRinColors.current
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(6.dp),
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (active) c.accent else c.dim
        )
    )
}

@Composable
private fun TransportButton(label: String, onClick: () -> Unit) {
    val c = LocalRinColors.current
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(6.dp),
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.fg)
    )
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val c = LocalRinColors.current
    val shape = if (c.isGlass) RoundedCornerShape(4.dp) else RectangleShape
    Canvas(
        modifier.height(12.dp)
            .border(if (c.isGlass) 1.dp else 2.dp, c.border, shape)
            .background(c.bg, shape)
            .pointerInput(Unit) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val fillWidth = size.width * progress
        if (fillWidth > 0f) {
            if (c.isGlass) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(c.accent, c.accent.copy(alpha = 0.75f))
                    ),
                    size = Size(fillWidth, size.height)
                )
            } else {
                drawRect(c.accent, size = Size(fillWidth, size.height))
            }
        }
    }
}

@Composable
private fun PlaylistPanel(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val c = LocalRinColors.current
    Fieldset("./Playlist/", modifier, fillHeight = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RinButton("ALL", Modifier.weight(1f), vm.viewTab == "all") { vm.setViewTab("all") }
            RinButton("FAV", Modifier.weight(1f), vm.viewTab == "fav") { vm.setViewTab("fav") }
            RinButton("S", Modifier.width(44.dp), vm.isSearchOpen) { vm.toggleSearch() }
        }
        if (vm.isSearchOpen) {
            BasicTextField(
                value = vm.searchQuery,
                onValueChange = { vm.setSearch(it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .border(2.dp, c.border)
                    .background(c.bg)
                    .padding(8.dp),
                textStyle = TextStyle(color = c.fg, fontSize = 12.sp),
                decorationBox = { inner ->
                    if (vm.searchQuery.isEmpty())
                        Text("Search…", style = TextStyle(color = c.dim, fontSize = 12.sp))
                    inner()
                }
            )
        }
        PlaylistContent(vm, Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistContent(vm: PlayerViewModel, modifier: Modifier) {
    val c = LocalRinColors.current
    if (!vm.hasScanned || vm.isScanning) {
        Box(modifier.fillMaxSize())
        return
    }
    if (vm.tracks.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("NO MUSIC FOUND", style = TextStyle(fontSize = 11.sp, color = c.accent))
        }
        return
    }

    if (vm.isSearchOpen && vm.searchQuery.isNotBlank()) {
        val results = vm.searchResults
        if (results.isEmpty()) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO RESULTS", style = TextStyle(fontSize = 11.sp, color = c.accent))
            }
        } else {
            LazyColumn(modifier.fillMaxSize().border(2.dp, c.border).background(c.bg)) {
                items(results, key = { it.uniqueKey }) { t -> TrackRow(vm, t, null, null) }
            }
        }
        return
    }

    when (vm.viewTab) {
        "fav" -> {
            val favs = vm.favoriteTracks
            val hist = vm.historyTracks
            LazyColumn(modifier.fillMaxSize().border(2.dp, c.border).background(c.bg)) {
                item { FolderHeader("STARRED", favs.size, true) }
                if (favs.isEmpty()) item { EmptyRow("NO STARRED SONGS") }
                else items(favs, key = { "fav-${it.uniqueKey}" }) { t -> TrackRow(vm, t, null, null) }
                item { FolderHeader("HISTORY", hist.size, true) }
                if (hist.isEmpty()) item { EmptyRow("NO HISTORY YET") }
                else items(hist, key = { "hist-${it.uniqueKey}" }) { t -> TrackRow(vm, t, null, null) }
            }
        }
        else -> {
            val grouped = vm.groupedTracks
            LazyColumn(modifier.fillMaxSize().border(2.dp, c.border).background(c.bg)) {
                grouped.forEach { (folder, list) ->
                    val open = folder in vm.openFolders
                    item(key = "hdr-$folder") {
                        FolderHeader(folder, list.size, open) { vm.toggleFolder(folder) }
                    }
                    if (open) {
                        items(list, key = { "${folder}-${it.uniqueKey}" }) { t ->
                            TrackRow(vm, t, folder, list.indexOf(t))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(name: String, count: Int, open: Boolean, onToggle: (() -> Unit)? = null) {
    val c = LocalRinColors.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (onToggle != null) (if (open) "▼ " else "▶ ") else "",
            style = TextStyle(fontSize = 9.sp, color = c.dim)
        )
        Text(
            name.uppercase(),
            Modifier.weight(1f),
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = c.accent,
                letterSpacing = 1.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("($count)", style = TextStyle(fontSize = 9.sp, color = c.dim))
    }
    HorizontalDivider(color = c.listHover, thickness = 1.dp)
}

@Composable
private fun EmptyRow(text: String) {
    val c = LocalRinColors.current
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 10.sp, color = c.accent, letterSpacing = 1.sp))
    }
}

@Composable
private fun TrackRow(vm: PlayerViewModel, track: Track, folder: String?, displayIdx: Int?) {
    val c = LocalRinColors.current
    val active = vm.currentTrack?.uri == track.uri
    val fav = vm.isFavorite(track)
    Row(
        Modifier.fillMaxWidth()
            .background(if (active) c.fg else Color.Transparent)
            .clickable { vm.playTrack(track) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (displayIdx != null) {
            Text(
                "${(displayIdx + 1).toString().padStart(2, '0')}. ",
                style = TextStyle(fontSize = 11.sp, color = if (active) c.bg else c.fg)
            )
        }
        Text(
            track.title,
            Modifier.weight(1f),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) c.bg else c.fg
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (fav) "★" else "☆",
            Modifier.padding(start = 6.dp).clickable { vm.toggleFav(track) },
            style = TextStyle(fontSize = 14.sp, color = if (active) c.bg else c.accent)
        )
    }
    HorizontalDivider(color = c.listHover, thickness = 1.dp)
}

@Composable
private fun EditMetadataDialog(
    track: Track,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    val c = LocalRinColors.current
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }

    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth()
                .border(2.dp, c.border)
                .background(c.panel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "EDIT METADATA",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = c.accent,
                    letterSpacing = 1.sp
                )
            )
            Field("TITLE", title) { title = it }
            Field("ARTIST", artist) { artist = it }
            Field("ALBUM", album) { album = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RinButton("CANCEL", Modifier.weight(1f), onClick = onCancel)
                RinButton("SAVE", Modifier.weight(1f), selected = true) { onSave(title, artist, album) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalRinColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = TextStyle(fontSize = 10.sp, color = c.accent, letterSpacing = 0.5.sp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .border(2.dp, c.border)
                .background(c.bg)
                .padding(8.dp),
            textStyle = TextStyle(color = c.fg, fontSize = 13.sp)
        )
    }
}