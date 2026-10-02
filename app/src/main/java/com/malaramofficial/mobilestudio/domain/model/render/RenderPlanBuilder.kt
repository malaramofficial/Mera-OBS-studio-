package com.malaramofficial.mobilestudio.domain.model.render

import com.malaramofficial.mobilestudio.domain.model.scene.Scene
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.Source
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform

/**
 * Pure domain translator converting a [Scene] model into an executable [RenderPlan].
 * Does not depend on Android graphics, OpenGL, or Compose.
 */
object RenderPlanBuilder {

    private const val SCREEN_SPLIT_RATIO = 0.65f

    fun build(
        scene: Scene,
        outputProfile: StudioOutputProfile = StudioOutputProfile.VERTICAL_9_16,
        timestampNs: Long = System.nanoTime()
    ): RenderPlan {
        val canvasWidth = outputProfile.width
        val canvasHeight = outputProfile.height
        val screenSource = scene.sortedSources.firstOrNull { it.type == SourceType.SCREEN && it.visible }
        val cameraSource = scene.sortedSources.firstOrNull { it.type == SourceType.CAMERA && it.visible }
        // A visible screen + camera pair is the default Shorts-style split layout.
        // Keep gameplay in the larger upper region and face camera in the lower region.
        val autoLayout = screenSource != null && cameraSource != null

        val autoTransforms = if (autoLayout) {
            val screenHeight = canvasHeight * SCREEN_SPLIT_RATIO
            val cameraHeight = canvasHeight - screenHeight
            val screenTransform = Transform(
                x = 0f, y = 0f,
                width = canvasWidth.toFloat(), height = screenHeight
            )
            val cameraTransform = Transform(
                x = 0f, y = screenHeight,
                width = canvasWidth.toFloat(), height = cameraHeight
            )
            mapOf(
                screenSource!!.id to screenTransform,
                cameraSource!!.id to cameraTransform
            )
        } else emptyMap()

        val renderableLayers = scene.sortedSources.mapIndexed { index, source ->
            val effectiveTransform = autoTransforms[source.id] ?: source.transform
            val effectiveCrop = if (autoLayout && source.id == screenSource!!.id) {
                coverCrop(sourceAspect(source), effectiveTransform.width, effectiveTransform.height)
            } else if (autoLayout && source.id == cameraSource!!.id) {
                coverCrop(16f / 9f, effectiveTransform.width, effectiveTransform.height)
            } else {
                source.crop.clamp(effectiveTransform.width, effectiveTransform.height)
            }

            val matrix = effectiveTransform.toMatrix4x4(
                canvasWidth = canvasWidth.toFloat(),
                canvasHeight = canvasHeight.toFloat()
            )

            RenderableLayer(
                layerId = "layer_${source.id}_$index",
                sourceId = source.id,
                sourceType = source.type,
                zIndex = source.zIndex,
                isVisible = source.visible,
                opacity = source.opacity,
                transform = effectiveTransform,
                crop = effectiveCrop,
                chromaKey = source.chromaKey,
                modelMatrix = matrix
            )
        }

        return RenderPlan(
            sceneId = scene.id,
            sceneName = scene.name,
            layers = renderableLayers,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            timestampNs = timestampNs
        )
    }

    private fun sourceAspect(source: Source): Float {
        val config = source.config
        return when (config) {
            is com.malaramofficial.mobilestudio.domain.model.source.SourceConfig.Screen ->
                config.captureWidth.toFloat() / config.captureHeight.coerceAtLeast(1).toFloat()
            else -> 16f / 9f
        }
    }

    private fun coverCrop(sourceAspect: Float, width: Float, height: Float): Crop {
        val destinationAspect = width / height.coerceAtLeast(1f)
        return if (sourceAspect > destinationAspect) {
            val crop = ((1f - destinationAspect / sourceAspect) * width / 2f).coerceAtLeast(0f)
            Crop(left = crop, right = crop)
        } else if (sourceAspect < destinationAspect) {
            val crop = ((1f - sourceAspect / destinationAspect) * height / 2f).coerceAtLeast(0f)
            Crop(top = crop, bottom = crop)
        } else {
            Crop()
        }
    }
}
