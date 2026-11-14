package io.github.ronynn.rin

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val durationMs: Long,
    val displayName: String
) {
    val albumArtUri: Uri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        id.takeIf { it > 0 } ?: 0L
    )
    val uniqueKey: String get() = if (id > 0) "m$id" else "u:$uri"
}

object MediaLibrary {
    private val cols = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATA
    )

    fun scan(context: Context): List<Track> {
        val out = ArrayList<Track>(512)
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DURATION} > 5000"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, sel, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iN = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iP = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = if (iP >= 0) c.getString(iP) else null
                out += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    title = c.getString(iT) ?: c.getString(iN) ?: "Unknown",
                    artist = c.getString(iA) ?: "UNKNOWN ARTIST",
                    album = c.getString(iAl) ?: "UNKNOWN ALBUM",
                    folder = folderFromPath(path),
                    durationMs = c.getLong(iD),
                    displayName = c.getString(iN) ?: ""
                )
            }
        }
        return out
    }

    fun fromUri(context: Context, uri: Uri): Track? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
            Track(
                id = -1L,
                uri = uri,
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: name.substringBeforeLast('.'),
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "UNKNOWN ARTIST",
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "UNKNOWN ALBUM",
                folder = "IMPORTED",
                durationMs = mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L,
                displayName = name
            )
        } catch (_: Exception) { null } finally { runCatching { mmr.release() } }
    }

    private fun folderFromPath(path: String?): String {
        if (path.isNullOrBlank()) return "Unknown"
        val parent = path.substringBeforeLast('/', "")
        return parent.substringAfterLast('/').ifBlank { "Unknown" }
    }
}