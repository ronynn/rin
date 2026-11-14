package io.github.ronynn.rin

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private var prevWave: FloatArray? = null

    companion object {
        val waveform = MutableStateFlow(FloatArray(0))
    }

    override fun onCreate() {
        super.onCreate()

        val teeProcessor = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
            override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
                prevWave = null
            }

            override fun handleBuffer(buffer: ByteBuffer) {
                val pcmData = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                val sampleCount = pcmData.remaining() / 2
                if (sampleCount <= 0) return

                val targetSize = 1024
                val step = max(1, sampleCount / targetSize)
                val out = FloatArray(targetSize)
                var i = 0
                while (i < targetSize && pcmData.remaining() >= 2) {
                    out[i] = pcmData.short.toFloat() / 32768f
                    i++
                    val toSkip = (step - 1) * 2
                    if (pcmData.remaining() < toSkip) break
                    pcmData.position(pcmData.position() + toSkip)
                }
                val trimmed = if (i == targetSize) out else out.copyOf(i)

                // Exponential smoothing — matches AnalyserNode's smoothingTimeConstant = 0.85
                val smoothed = prevWave?.takeIf { it.size == trimmed.size }?.let { p ->
                    FloatArray(trimmed.size) { idx -> p[idx] * 0.85f + trimmed[idx] * 0.15f }
                } ?: trimmed

                prevWave = smoothed
                waveform.value = smoothed
            }
        })

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(teeProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    prevWave = null
                    waveform.value = FloatArray(0)
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    prevWave = null
                    waveform.value = FloatArray(0)
                }
            }
        })

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p != null && p.playWhenReady && p.mediaItemCount > 0) return
        stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        prevWave = null
        waveform.value = FloatArray(0)
        super.onDestroy()
    }
}