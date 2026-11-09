// app/src/main/java/io/github/ronynn/rin/MediaLibrary.kt
package io.github.ronynn.rin

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String
) {
    val albumArtUri: Uri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), albumId
    )
    val stableKey: String get() = "$title|$artist|$album|$durationMs"
}

object MediaLibrary {
    private val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME
    )

    fun scan(context: Context): List<Track> {
        val out = ArrayList<Track>(256)
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, sel, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iN = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += Track(
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = c.getString(iT) ?: c.getString(iN) ?: "Unknown",
                    artist = c.getString(iA) ?: "UNKNOWN ARTIST",
                    album = c.getString(iAl) ?: "UNKNOWN ALBUM",
                    albumId = c.getLong(iAlI),
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
                uri = uri,
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast('.'),
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "UNKNOWN ARTIST",
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "UNKNOWN ALBUM",
                albumId = 0L,
                durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                displayName = name
            )
        } catch (_: Exception) { null } finally { runCatching { mmr.release() } }
    }
}