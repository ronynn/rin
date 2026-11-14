package io.github.ronynn.rin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object BitmapCache {
    private val map = HashMap<String, Bitmap>()
    fun get(k: String): Bitmap? = map[k]
    fun put(k: String, b: Bitmap) { if (map.size > 20) map.clear(); map[k] = b }
}

@Composable
internal fun RinImage(model: Any?, modifier: Modifier = Modifier) {
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
internal fun Fieldset(
    legend: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalRinColors.current
    val shape: Shape = if (c.isGlass) RoundedCornerShape(12.dp) else RectangleShape
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
internal fun RinButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalRinColors.current
    val shape: Shape = if (c.isGlass) RoundedCornerShape(8.dp) else RectangleShape
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
internal fun IconBtn(label: String, active: Boolean, onClick: () -> Unit) {
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
internal fun TransportButton(label: String, onClick: () -> Unit) {
    val c = LocalRinColors.current
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(6.dp),
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.fg)
    )
}

@Composable
internal fun ProgressBar(progress: Float, modifier: Modifier, onSeek: (Float) -> Unit) {
    val c = LocalRinColors.current
    val shape: Shape = if (c.isGlass) RoundedCornerShape(4.dp) else RectangleShape
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
internal fun FolderHeader(name: String, count: Int, open: Boolean, onToggle: (() -> Unit)? = null) {
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
internal fun EmptyRow(text: String) {
    val c = LocalRinColors.current
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 10.sp, color = c.accent, letterSpacing = 1.sp))
    }
}

@Composable
internal fun TrackRow(vm: PlayerViewModel, track: Track, displayIdx: Int?) {
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