package com.malaramofficial.mobilestudio.engine.gpu

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class MediaTextureSourceManager(
    private val context: Context,
    private val renderPipeline: StudioRenderPipeline
) {
    private val signatures = ConcurrentHashMap<String, String>()
    private val players = ConcurrentHashMap<String, ExoPlayer>()
    private val pendingIds = ConcurrentHashMap.newKeySet<String>()
    private val syncMutex = Mutex()

    suspend fun syncScene(scene: Scene?) = syncMutex.withLock {
        withContext(Dispatchers.IO) {
        val mediaSources = scene?.sources?.filter { it.visible && it.type == SourceType.MEDIA } ?: emptyList()
        val activeIds = mediaSources.map { it.id }.toSet()
        (signatures.keys + players.keys).toList().distinct().filterNot { activeIds.contains(it) }.forEach { releaseSource(it) }
            mediaSources.forEach { syncMedia(it) }
        }
    }

    @OptIn(UnstableApi::class)
    private fun syncMedia(source: com.malaramofficial.mobilestudio.domain.model.scene.Source) {
        val config = source.config as? SourceConfig.Media ?: return
        val uri = config.uri.trim()
        if (uri.isEmpty()) {
            releaseSource(source.id)
            return
        }

        val signature = listOf("media", uri, config.isLooping, config.autoPlay, config.volume).joinToString("|")
        if (signatures[source.id] == signature &&
            (players.containsKey(source.id) || pendingIds.contains(source.id))
        ) return

        releaseSource(source.id)
        signatures[source.id] = signature
        pendingIds.add(source.id)

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
            synchronized(this) {
                pendingIds.remove(source.id)
                players[source.id] = player
            }
        }
    }

    private fun releaseSource(sourceId: String) {
        signatures.remove(sourceId)
        pendingIds.remove(sourceId)
        synchronized(this) {
            players.remove(sourceId)?.let { player ->
                android.os.Handler(player.applicationLooper).post { player.release() }
            }
        }
        renderPipeline.releaseMediaInput(sourceId)
    }

    fun release() {
        val ids = synchronized(this) { players.keys.toList() }
        ids.forEach { releaseSource(it) }
    }
}
