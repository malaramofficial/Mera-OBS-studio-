package com.malaramofficial.mobilestudio.domain.model.render

import com.malaramofficial.mobilestudio.domain.model.scene.Scene

/**
 * Pure domain translator converting a [Scene] model into an executable [RenderPlan].
 * Does not depend on Android graphics, OpenGL, or Compose.
 */
object RenderPlanBuilder {

    fun build(
        scene: Scene,
        outputProfile: StudioOutputProfile = StudioOutputProfile.VERTICAL_9_16,
        timestampNs: Long = System.nanoTime()
    ): RenderPlan {
        val canvasWidth = outputProfile.width
        val canvasHeight = outputProfile.height
        val renderableLayers = scene.sortedSources.mapIndexed { index, source ->
            // Clamp crop safely within layer's source bounds
            val safeCrop = source.crop.clamp(source.transform.width, source.transform.height)

            // Calculate 4x4 model matrix in normalized coordinates
            val matrix = source.transform.toMatrix4x4(
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
                transform = source.transform,
                crop = safeCrop,
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
}
