// app/src/main/java/io/github/ronynn/rin/PlayerService.kt
package io.github.ronynn.rin

import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
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

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setAudioSessionId(sid)
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