package com.malaramofficial.mobilestudio.engine.gpu

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaTextureSourceManager(
    private val context: Context,
    private val renderPipeline: StudioRenderPipeline
) {
    private val signatures = HashMap<String, String>()
    private val players = HashMap<String, ExoPlayer>()

    suspend fun syncScene(scene: Scene?) = withContext(Dispatchers.IO) {
        val mediaSources = scene?.sources?.filter { it.visible && it.type == SourceType.MEDIA } ?: emptyList()
        val activeIds = mediaSources.map { it.id }.toSet()
        (signatures.keys + players.keys).toList().distinct().filterNot { activeIds.contains(it) }.forEach { releaseSource(it) }
        mediaSources.forEach { syncMedia(it) }
    }

    private fun syncMedia(source: com.malaramofficial.mobilestudio.domain.model.scene.Source) {
        val config = source.config as? SourceConfig.Media ?: return
        val uri = config.uri.trim()
        if (uri.isEmpty()) {
            releaseSource(source.id)
            return
        }

        val signature = listOf("media", uri, config.isLooping, config.autoPlay, config.volume).joinToString("|")
        if (signatures[source.id] == signature && players.containsKey(source.id)) return

        releaseSource(source.id)
        signatures[source.id] = signature

        renderPipeline.createMediaInputSurface(source.id) { surface ->
            val player = ExoPlayer.Builder(context)
                .setLooper(android.os.Looper.myLooper() ?: android.os.Looper.getMainLooper())
                .build()
            player.setVideoSurface(surface)
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            player.repeatMode = if (config.isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.volume = config.volume.coerceIn(0f, 1f)
            player.prepare()
            player.playWhenReady = config.autoPlay
            synchronized(this) { players[source.id] = player }
        }
    }

    private fun releaseSource(sourceId: String) {
        signatures.remove(sourceId)
        synchronized(this) { players.remove(sourceId)?.release() }
        renderPipeline.releaseMediaInput(sourceId)
    }

    fun release() {
        val ids = synchronized(this) { players.keys.toList() }
        ids.forEach { releaseSource(it) }
    }
}
