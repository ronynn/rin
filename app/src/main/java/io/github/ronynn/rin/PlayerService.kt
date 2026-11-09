package io.github.ronynn.rin

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow

class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        val audioSessionId = MutableStateFlow(0)
    }

    override fun onCreate() {
        super.onCreate()

        val sid = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        audioSessionId.value = sid

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioSessionId(sid)
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
        audioSessionId.value = 0
        super.onDestroy()
    }
}