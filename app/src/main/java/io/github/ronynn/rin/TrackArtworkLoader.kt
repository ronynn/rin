package io.github.ronynn.rin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class TrackArtworkLoader(private val context: Context) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/") || mimeType.startsWith("audio/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
        return if (bmp != null) Futures.immediateFuture(bmp)
        else Futures.immediateFailedFuture(IllegalArgumentException("Cannot decode bytes"))
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path
                    val bmp = path?.let { BitmapFactory.decodeFile(it) }
                    if (bmp != null) Futures.immediateFuture(bmp)
                    else Futures.immediateFailedFuture(IllegalStateException("No file art"))
                }
                else -> loadEmbeddedArt(uri)
            }
        } catch (e: Exception) { Futures.immediateFailedFuture(e) }
    }

    private fun loadEmbeddedArt(uri: Uri): ListenableFuture<Bitmap> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val bytes = mmr.embeddedPicture
            if (bytes != null) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return Futures.immediateFuture(bmp)
            }
            Futures.immediateFailedFuture(IllegalStateException("No embedded art"))
        } catch (e: Exception) {
            Futures.immediateFailedFuture(e)
        } finally {
            runCatching { mmr.release() }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        metadata.artworkData?.let { return decodeBitmap(it) }
        metadata.artworkUri?.let { return loadBitmap(it) }
        return Futures.immediateFailedFuture(IllegalArgumentException("No artwork"))
    }
}