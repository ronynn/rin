package io.github.ronynn.rin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val c = LocalRinColors.current
    val context = LocalContext.current

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setCustomCover(it) } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.playExternalUris(uris) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.scanDevice() else vm.status = "PERMISSION DENIED" }

    LaunchedEffect(Unit) {
        vm.connect()
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED)
            vm.scanDevice()
        else permLauncher.launch(perm)
    }

    val openFile: () -> Unit = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }
    val pickCover: () -> Unit = {
        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).padding(8.dp)) {
        val twoCol = maxWidth >= 800.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val coverSize = when {
            maxHeight <= 560.dp -> 72.dp
            maxHeight <= 720.dp -> 96.dp
            else -> 124.dp
        }
        val visHeight = when {
            maxHeight <= 560.dp -> 56.dp
            maxHeight <= 720.dp -> 80.dp
            else -> 120.dp
        }

        if (twoCol) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsPanel(vm, onOpenFile = openFile)
                    PlaylistPanel(vm, Modifier.weight(1f))
                }
                PlayerPanel(vm, coverSize, visHeight, onCoverClick = pickCover,
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsPanel(vm, onOpenFile = openFile)
                PlayerPanel(vm, coverSize, visHeight, onCoverClick = pickCover,
                    modifier = Modifier.fillMaxWidth())
                PlaylistPanel(vm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Fieldset(legend: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRinColors.current
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .padding(top = 7.dp)
                .border(2.dp, c.border)
                .background(c.panel)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
        Text(
            legend.uppercase(),
            Modifier.padding(start = 14.dp).background(c.panel).padding(horizontal = 6.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 1.sp, color = c.accent, lineHeight = 12.sp
            )
        )
    }
}

@Composable
private fun RinButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalRinColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        border = BorderStroke(2.dp, c.border),
        shape = RectangleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) c.fg else Color.Transparent,
            contentColor = if (selected) c.bg else c.fg
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsPanel(vm: PlayerViewModel, onOpenFile: () -> Unit) {
    val c = LocalRinColors.current
    Fieldset("./Settings/") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Themes.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (name, _) ->
                        val idx = Themes.indexOfFirst { it.first == name }
                        RinButton(name.uppercase(), Modifier.weight(1f), vm.themeIndex == idx) {
                            vm.setTheme(idx)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RinButton("RESCAN", Modifier.weight(1f)) { vm.scanDevice() }
            RinButton("OPEN FILE", Modifier.weight(1f), onClick = onOpenFile)
        }
        if (vm.status.isNotEmpty()) {
            Text(
                text = vm.status,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    letterSpacing = 1.sp, color = c.accent
                ),
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
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRinColors.current
    val track = vm.currentTrack
    val sessionId by PlayerService.audioSessionId.collectAsState()
    val wave = rememberWaveform(sessionId, vm.isPlaying)

    Fieldset("./Player/", modifier) {
        Row(Modifier.fillMaxWidth().height(coverSize).border(2.dp, c.border).background(c.bg)) {
            Box(
                Modifier.size(coverSize - 4.dp).border(2.dp, c.border).clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                if (track != null) {
                    val v = vm.coverVersion
                    AsyncImage(
                        model = remember(v, track) { vm.coverModel(track, v) },
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("♪", style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 34.sp, color = c.accent))
                }
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track?.title ?: "NO TRACK LOADED",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = c.fg, lineHeight = 17.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track?.artist ?: "UNKNOWN ARTIST",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = c.accent),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.album ?: "UNKNOWN ALBUM",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = c.accent),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        RinVisualizer(wave, vm.isPlaying, Modifier.fillMaxWidth().height(visHeight))

        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            TransportButton("◀◀") { vm.previous() }
            Spacer(Modifier.width(15.dp))
            Box(
                Modifier.size(42.dp).border(2.dp, c.border, CircleShape)
                    .background(c.bg).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (vm.isPlaying) "❚❚" else "▶",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = c.fg
                    )
                )
            }
            Spacer(Modifier.width(15.dp))
            TransportButton("▶▶") { vm.next() }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = vm.format(vm.positionMs),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = c.fg)
            )
            ProgressBar(vm.progress, Modifier.weight(1f)) { vm.seekToFraction(it) }
            Text(
                text = vm.format(vm.durationMs),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = c.fg)
            )
        }
    }
}

@Composable
private fun TransportButton(label: String, onClick: () -> Unit) {
    val c = LocalRinColors.current
    Text(
        text = label,
        modifier = Modifier.clickable { onClick() }.padding(6.dp),
        style = TextStyle(
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, color = c.fg
        )
    )
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val c = LocalRinColors.current
    Canvas(
        modifier.height(12.dp).border(2.dp, c.border).background(c.bg)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        drawRect(c.accent, size = Size(size.width * progress, size.height))
    }
}

@Composable
private fun PlaylistPanel(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val c = LocalRinColors.current
    Fieldset("./Playlist/", modifier) {
        if (vm.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO TRACKS FOUND",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.accent
                    )
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().border(2.dp, c.border).background(c.bg)) {
                itemsIndexed(vm.tracks, key = { _, t -> t.uri.toString() }) { i, t ->
                    val active = i == vm.currentIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (active) c.fg else Color.Transparent)
                            .clickable { vm.playAt(i) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(i + 1).toString().padStart(2, '0')}. ${t.title}",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) c.bg else c.fg
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            Modifier.width(22.dp).height(18.dp)
                                .border(2.dp, if (active) c.bg else c.accent)
                                .clickable { vm.removeAt(i) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) c.bg else c.accent
                                )
                            )
                        }
                    }
                    HorizontalDivider(color = c.listHover, thickness = 1.dp)
                }
            }
        }
    }
}