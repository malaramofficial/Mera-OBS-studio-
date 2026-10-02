package com.malaramofficial.mobilestudio.engine.gpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces real GPU textures for CPU-backed visual sources.
 *
 * Decoding/rasterization happens off the GL thread. The final bitmap upload is
 * handed to StudioRenderPipeline, which performs the GL operation on its
 * dedicated context/thread.
 */
class VisualTextureSourceManager(
    private val context: Context,
    private val renderPipeline: StudioRenderPipeline
) {
    private val signatures = HashMap<String, String>()

    suspend fun syncScene(scene: Scene?) = withContext(Dispatchers.IO) {
        val visualSources = scene?.sources
            ?.filter { it.visible && (it.type == SourceType.IMAGE || it.type == SourceType.TEXT) }
            ?: emptyList()
        val activeIds = visualSources.map { it.id }.toSet()

        signatures.keys.toList().filterNot { activeIds.contains(it) }.forEach {
            renderPipeline.unbindBitmap2D(it)
            signatures.remove(it)
        }

        visualSources.forEach { source ->
            when (source.type) {
                SourceType.IMAGE -> syncImage(source)
                SourceType.TEXT -> syncText(source)
                else -> Unit
            }
        }
    }

    private fun syncImage(source: Source) {
        val config = source.config as? SourceConfig.Image ?: return
        val uri = config.uri.trim()
        if (uri.isEmpty()) {
            renderPipeline.unbindBitmap2D(source.id)
            signatures.remove(source.id)
            return
        }

        val signature = "image|" + uri + "|" + config.scaleMode + "|" + config.alpha
        if (signatures[source.id] == signature) return

        val bitmap = decodeImage(Uri.parse(uri)) ?: return
        renderPipeline.bindBitmap2D(source.id, bitmap)
        signatures[source.id] = signature
    }

    private fun syncText(source: Source) {
        val config = source.config as? SourceConfig.Text ?: return
        val signature = "text|" + config.text + "|" + config.fontSizeSp + "|" +
            config.textColorHex + "|" + config.backgroundColorHex + "|" + config.fontFamilyName
        if (signatures[source.id] == signature) return

        val bitmap = rasterizeText(config)
        renderPipeline.bindBitmap2D(source.id, bitmap)
        signatures[source.id] = signature
    }

    private fun decodeImage(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun rasterizeText(config: SourceConfig.Text): Bitmap {
        val density = context.resources.displayMetrics.density
        val textSize = (config.fontSizeSp * density).coerceAtLeast(8f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = config.textColorHex.toInt()
            this.typeface = resolveTypeface(config.fontFamilyName)
        }

        val padding = (16f * density).toInt()
        val width = (paint.measureText(config.text) + padding * 2).toInt().coerceAtLeast(2)
        val metrics = paint.fontMetrics
        val height = (metrics.bottom - metrics.top + padding * 2).toInt().coerceAtLeast(2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(config.backgroundColorHex.toInt())
        canvas.drawText(config.text, padding.toFloat(), padding - metrics.top, paint)
        return bitmap
    }

    private fun resolveTypeface(name: String): Typeface = when (name.uppercase()) {
        "BOLD" -> Typeface.DEFAULT_BOLD
        "MONOSPACE" -> Typeface.MONOSPACE
        "SERIF" -> Typeface.SERIF
        "SANS_SERIF" -> Typeface.SANS_SERIF
        else -> Typeface.DEFAULT
    }
}
