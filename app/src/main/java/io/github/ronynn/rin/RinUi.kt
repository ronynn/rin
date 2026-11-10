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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/* ---------- bitmap cache (replaces Coil) ---------- */

private object BitmapCache {
    private val map = HashMap<String, Bitmap>()
    fun get(key: String): Bitmap? = map[key]
    fun put(key: String, bmp: Bitmap) {
        if (map.size > 20) map.clear()
        map[key] = bmp
    }
}

@Composable
private fun RinImage(model: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(model) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(model) {
        val uri = model as? Uri ?: return@LaunchedEffect
        val key = uri.toString()
        BitmapCache.get(key)?.let { bmp = it; return@LaunchedEffect }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
        bmp?.let { BitmapCache.put(key, it) }
    }
    bmp?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

/* ---------- primitives ---------- */

@Composable
fun Fieldset(
    legend: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalRinColors.current
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(top = 7.dp)
                .border(2.dp, c.border)
                .background(c.panel)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
        Text(
            text = legend.uppercase(),
            modifier = Modifier
                .padding(start = 14.dp)
                .background(c.panel)
                .padding(horizontal = 6.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = c.accent,
                lineHeight = 12.sp
            )
        )
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

/* ---------- screen ---------- */

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val c = LocalRinColors.current
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setCustomCover(it) } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.playExternalUris(uris) }

    val openFile: () -> Unit = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }
    val pickCover: () -> Unit = {
        coverPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()
    ) {
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
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsPanel(vm, onOpenFile = openFile)
                    PlaylistPanel(vm, Modifier.weight(1f))
                }
                PlayerPanel(
                    vm, coverSize, visHeight, landscape = true, onCoverClick = pickCover,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SettingsPanel(vm, onOpenFile = openFile)
                PlayerPanel(
                    vm, coverSize, visHeight, landscape = false, onCoverClick = pickCover,
                    modifier = Modifier.fillMaxWidth()
                )
                PlaylistPanel(vm, Modifier.weight(1f))
            }
        }
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
                        RinButton(
                            name.uppercase(),
                            Modifier.weight(1f),
                            vm.themeIndex == idx
                        ) { vm.setTheme(idx) }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RinButton(
                if (vm.isScanning) "SCANNING…" else "RESCAN",
                Modifier.weight(1f)
            ) { vm.rescan() }
            RinButton("OPEN FILE", Modifier.weight(1f), onClick = onOpenFile)
        }
        Box(Modifier.height(14.dp).fillMaxWidth()) {
            if (vm.status.isNotEmpty()) {
                Text(
                    vm.status,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = c.accent
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        Row(
            Modifier.fillMaxWidth().height(coverSize)
                .border(2.dp, c.border).background(c.bg)
        ) {
            Box(
                Modifier.size(coverSize - 4.dp)
                    .border(2.dp, c.border)
                    .clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                if (track != null) {
                    val v = vm.coverVersion
                    RinImage(
                        model = remember(v, track) { vm.coverModel(track, v) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "♪",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = c.accent
                        )
                    )
                }
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    track?.title ?: "NO TRACK LOADED",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
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
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = c.accent
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.album ?: "UNKNOWN ALBUM",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = c.accent
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (landscape) {
            RinVisualizer(wave, vm.isPlaying, Modifier.fillMaxWidth().weight(1f))
        } else {
            RinVisualizer(wave, vm.isPlaying, Modifier.fillMaxWidth().height(visHeight))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportButton("◀◀") { vm.previous() }
            Spacer(Modifier.width(15.dp))
            Box(
                Modifier.size(42.dp)
                    .border(2.dp, c.border, CircleShape)
                    .background(c.bg)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (vm.isPlaying) "❚❚" else "▶",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = c.fg
                    )
                )
            }
            Spacer(Modifier.width(15.dp))
            TransportButton("▶▶") { vm.next() }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                vm.format(vm.positionMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = c.fg
                )
            )
            ProgressBar(vm.progress, Modifier.weight(1f)) { vm.seekToFraction(it) }
            Text(
                vm.format(vm.durationMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = c.fg
                )
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

/* ---------- visualizer (renderer only) ---------- */

@Composable
fun RinVisualizer(
    wave: State<FloatArray>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalRinColors.current
    Canvas(modifier.background(c.bg).border(2.dp, c.border)) {
        val mid = size.height / 2f
        val w = wave.value

        if (!isPlaying || w.isEmpty()) {
            drawLine(
                c.fg, Offset(0f, mid), Offset(size.width, mid),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
            return@Canvas
        }

        val step = size.width / w.size
        val main = Path().apply {
            moveTo(0f, mid + w[0] * mid * 0.95f)
            for (i in 1 until w.size) lineTo(i * step, mid + w[i] * mid * 0.95f)
        }
        val refl = Path().apply {
            moveTo(0f, size.height - (mid + w[0] * mid * 0.95f))
            for (i in 1 until w.size) lineTo(i * step, size.height - (mid + w[i] * mid * 0.95f))
        }

        val cap = StrokeCap.Round
        val join = StrokeJoin.Round

        drawPath(refl, c.accent.copy(alpha = 0.22f), style = Stroke(2.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.accent.copy(alpha = 0.12f), style = Stroke(10.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.accent.copy(alpha = 0.20f), style = Stroke(6.dp.toPx(), cap = cap, join = join))
        drawPath(main, c.fg, style = Stroke(2.5.dp.toPx(), cap = cap, join = join))
    }
}

/* ---------- small helpers ---------- */

@Composable
private fun TransportButton(label: String, onClick: () -> Unit) {
    val c = LocalRinColors.current
    Text(
        text = label,
        modifier = Modifier.clickable { onClick() }.padding(6.dp),
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = c.fg
        )
    )
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val c = LocalRinColors.current
    Canvas(
        modifier.height(12.dp)
            .border(2.dp, c.border)
            .background(c.bg)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        drawRect(c.accent, size = Size(size.width * progress, size.height))
    }
}

/* ---------- playlist with drag-to-reorder ---------- */

@Composable
private fun PlaylistPanel(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val c = LocalRinColors.current
    Fieldset("./Playlist/", modifier, fillHeight = true) {
        val showEmpty = vm.hasScanned && !vm.isScanning && vm.tracks.isEmpty()
        val showLoading = !vm.hasScanned || vm.isScanning

        when {
            showLoading -> Box(Modifier.fillMaxSize())
            showEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "NO TRACKS FOUND",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = c.accent
                    )
                )
            }
            else -> {
                val lazyListState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    vm.moveTrack(from.index, to.index)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, c.border)
                        .background(c.bg)
                ) {
                    itemsIndexed(
                        items = vm.tracks,
                        key = { _, t -> t.uri.toString() }
                    ) { i, t ->
                        ReorderableItem(
                            state = reorderState,
                            key = t.uri.toString()
                        ) { isDragging ->
                            val active = i == vm.currentIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            isDragging -> c.listHover
                                            active -> c.fg
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { vm.playAt(i) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${(i + 1).toString().padStart(2, '0')}. ",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = if (active) c.bg else c.fg
                                    )
                                )
                                Text(
                                    t.title,
                                    Modifier.weight(1f),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) c.bg else c.fg
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "☰",
                                    modifier = Modifier.longPressDraggableHandle(),
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        color = if (active) c.bg else c.accent
                                    )
                                )
                            }
                            HorizontalDivider(color = c.listHover, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}