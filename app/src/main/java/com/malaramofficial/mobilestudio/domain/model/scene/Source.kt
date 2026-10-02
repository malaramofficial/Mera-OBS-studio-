package com.malaramofficial.mobilestudio.domain.model.scene

import com.malaramofficial.mobilestudio.domain.model.source.SourceConfig

/**
 * Domain representation of an individual visual source/layer in a Scene.
 */
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val transform: Transform = Transform(),
    val crop: Crop = Crop(),
    val opacity: Float = 1.0f,
    val zIndex: Int = 0,
    val chromaKey: ChromaKeyConfig? = null,
    val config: SourceConfig? = null,
    val customData: String = "" // Serialized config or fallback custom payload
) {
    init {
        require(id.isNotBlank()) { "Source id cannot be blank" }
        require(name.isNotBlank()) { "Source name cannot be blank" }
        require(opacity in 0f..1f) { "Opacity must be within 0.0 and 1.0, got $opacity" }
    }

    /**
     * Creates an exact clone with a new unique ID and optional name, maintaining layer properties.
     */
    fun duplicate(newId: String, newName: String = "$name (Copy)"): Source {
        return copy(
            id = newId,
            name = newName
        )
    }
}
