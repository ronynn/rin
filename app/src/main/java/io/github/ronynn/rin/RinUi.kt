package io.github.ronynn.rin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val c = LocalRinColors.current

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        it?.let { vm.setCustomCover(it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) vm.playExternalUris(it)
    }

    val openFile: () -> Unit = { filePicker.launch(arrayOf("audio/*", "application/ogg")) }
    val pickCover: () -> Unit = {
        coverPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
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
                    vm, coverSize, visHeight, landscape = true,
                    onCoverClick = pickCover,
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
                    vm, coverSize, visHeight, landscape = false,
                    onCoverClick = pickCover,
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
private fun EditMetadataDialog(
    track: Track,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    val c = LocalRinColors.current
    // Key on uniqueKey so opening a different track rebuilds the fields
    var title by remember(track.uniqueKey) { mutableStateOf(track.title) }
    var artist by remember(track.uniqueKey) { mutableStateOf(track.artist) }
    var album by remember(track.uniqueKey) { mutableStateOf(track.album) }

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
                RinButton("SAVE", Modifier.weight(1f), selected = true) {
                    onSave(title, artist, album)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalRinColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = TextStyle(fontSize = 10.sp, color = c.accent, letterSpacing = 0.5.sp)
        )
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