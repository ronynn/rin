package io.github.ronynn.rin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsPanel(vm: PlayerViewModel, onOpenFile: () -> Unit) {
    val c = LocalRinColors.current
    var themeMenu by remember { mutableStateOf(false) }
    var fontMenu by remember { mutableStateOf(false) }

    Fieldset("./Settings/") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RinButton(if (vm.isScanning) "SCAN…" else "RESCAN", Modifier.weight(1f)) { vm.rescan() }

            Box(Modifier.weight(1f)) {
                RinButton(Themes[vm.themeIndex].first.uppercase(), Modifier.fillMaxWidth()) {
                    themeMenu = true
                }
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    Themes.forEachIndexed { i, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name.uppercase(), style = TextStyle(fontSize = 12.sp)) },
                            onClick = { vm.applyTheme(i); themeMenu = false }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                RinButton(Fonts[vm.fontIndex].first.uppercase(), Modifier.fillMaxWidth()) {
                    fontMenu = true
                }
                DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                    Fonts.forEachIndexed { i, (name, _) ->
                        DropdownMenuItem(
                            text = { Text(name.uppercase(), style = TextStyle(fontSize = 12.sp)) },
                            onClick = { vm.applyFont(i); fontMenu = false }
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
internal fun PlayerPanel(
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
                    Text(
                        "♪",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = c.accent
                        )
                    )
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
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = c.fg,
                        lineHeight = 17.sp
                    ),
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
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = c.fg
                    )
                )
            }
            Spacer(Modifier.width(12.dp))
            TransportButton("▶▶") { vm.next() }
            Spacer(Modifier.width(12.dp))
            IconBtn(if (vm.repeatMode == "one") "↻1" else "↻", active = vm.repeatMode != "off") {
                vm.cycleRepeat()
            }
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
internal fun PlaylistPanel(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val c = LocalRinColors.current
    Fieldset("./Playlist/", modifier, fillHeight = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RinButton("ALL", Modifier.weight(1f), vm.viewTab == "all") { vm.selectView("all") }
            RinButton("FAV", Modifier.weight(1f), vm.viewTab == "fav") { vm.selectView("fav") }
            RinButton("S", Modifier.width(44.dp), vm.isSearchOpen) { vm.toggleSearch() }
        }
        if (vm.isSearchOpen) {
            BasicTextField(
                value = vm.searchQuery,
                onValueChange = { vm.updateSearch(it) },
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
                items(results, key = { it.uniqueKey }) { t -> TrackRow(vm, t, null) }
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
                else items(favs, key = { "fav-${it.uniqueKey}" }) { t -> TrackRow(vm, t, null) }
                item { FolderHeader("HISTORY", hist.size, true) }
                if (hist.isEmpty()) item { EmptyRow("NO HISTORY YET") }
                else items(hist, key = { "hist-${it.uniqueKey}" }) { t -> TrackRow(vm, t, null) }
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
                            TrackRow(vm, t, list.indexOf(t))
                        }
                    }
                }
            }
        }
    }
}