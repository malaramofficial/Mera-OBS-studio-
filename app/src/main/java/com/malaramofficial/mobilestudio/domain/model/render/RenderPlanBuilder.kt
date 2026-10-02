package com.malaramofficial.mobilestudio.domain.model.render

import com.malaramofficial.mobilestudio.domain.model.scene.Scene

/**
 * Pure domain translator converting a Scene into an executable RenderPlan.
 *
 * Every source's stored transform is authoritative. In particular, screen and
 * camera layers must not be silently auto-arranged here: doing so would make
 * user zoom/position/rotation controls appear to work in the editor but be
 * ignored by the actual LIVE/recording render plan.
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
            val effectiveTransform = source.transform
            val effectiveCrop = source.crop.clamp(
                effectiveTransform.width,
                effectiveTransform.height
            )
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
}
