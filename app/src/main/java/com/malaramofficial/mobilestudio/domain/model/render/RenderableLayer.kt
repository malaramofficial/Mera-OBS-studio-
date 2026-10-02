package com.malaramofficial.mobilestudio.domain.model.render

import com.malaramofficial.mobilestudio.domain.model.scene.ChromaKeyConfig
import com.malaramofficial.mobilestudio.domain.model.scene.Crop
import com.malaramofficial.mobilestudio.domain.model.scene.SourceType
import com.malaramofficial.mobilestudio.domain.model.scene.Transform

/**
 * Platform-independent rendering layer contract prepared for future GPU compositor ingestion.
 * Decoupled from OpenGL, Vulkan, SurfaceView, or MediaCodec.
 */
data class RenderableLayer(
    val layerId: String,
    val sourceId: String,
    val sourceType: SourceType,
    val zIndex: Int,
    val isVisible: Boolean,
    val opacity: Float,
    val transform: Transform,
    val crop: Crop,
    val chromaKey: ChromaKeyConfig?,
    val modelMatrix: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RenderableLayer

        if (layerId != other.layerId) return false
        if (sourceId != other.sourceId) return false
        if (sourceType != other.sourceType) return false
        if (zIndex != other.zIndex) return false
        if (isVisible != other.isVisible) return false
        if (opacity != other.opacity) return false
        if (transform != other.transform) return false
        if (crop != other.crop) return false
        if (chromaKey != other.chromaKey) return false
        if (!modelMatrix.contentEquals(other.modelMatrix)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = layerId.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + sourceType.hashCode()
        result = 31 * result + zIndex
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + transform.hashCode()
        result = 31 * result + crop.hashCode()
        result = 31 * result + (chromaKey?.hashCode() ?: 0)
        result = 31 * result + modelMatrix.contentHashCode()
        return result
    }
}
