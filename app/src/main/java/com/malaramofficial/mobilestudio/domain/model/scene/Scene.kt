package com.malaramofficial.mobilestudio.domain.model.scene

import java.util.UUID

/**
 * Domain representation of a complete Studio Scene, containing an ordered stack of visual sources.
 */
data class Scene(
    val id: String,
    val name: String,
    val sources: List<Source> = emptyList(),
    val transition: Transition = Transition.Cut,
    val orderIndex: Int = 0
) {
    init {
        require(id.isNotBlank()) { "Scene id cannot be blank" }
        require(name.isNotBlank()) { "Scene name cannot be blank" }
    }

    /**
     * Returns sources sorted by zIndex ascending (0 = Background, N = Foreground).
     */
    val sortedSources: List<Source>
        get() = sources.sortedWith(compareBy({ it.zIndex }, { it.id }))

    fun findSource(sourceId: String): Source? = sources.find { it.id == sourceId }

    fun withSourceUpdated(updatedSource: Source): Scene {
        val updatedList = sources.map { if (it.id == updatedSource.id) updatedSource else it }
        return copy(sources = updatedList).normalizeLayers()
    }

    fun withSourceAdded(newSource: Source): Scene {
        val currentSorted = sortedSources
        val sourceWithTopZ = newSource.copy(zIndex = currentSorted.size)
        return copy(sources = currentSorted + sourceWithTopZ).normalizeLayers()
    }

    fun withSourceRemoved(sourceId: String): Scene {
        val remaining = sortedSources.filterNot { it.id == sourceId }
        return normalizeFromOrder(remaining)
    }

    /**
     * Normalizes the zIndex of all sources to be contiguous from 0 to N-1
     * based on their current relative sorting order. Prevents duplicate or drifting z-indices.
     */
    fun normalizeLayers(): Scene {
        val sorted = sources.sortedWith(compareBy({ it.zIndex }, { it.id }))
        return normalizeFromOrder(sorted)
    }

    private fun normalizeFromOrder(ordered: List<Source>): Scene {
        val normalized = ordered.mapIndexed { index, source ->
            if (source.zIndex != index) source.copy(zIndex = index) else source
        }
        return copy(sources = normalized)
    }

    /**
     * Moves the specified source to the very top (highest zIndex / foreground).
     */
    fun bringToFront(sourceId: String): Scene {
        val current = sortedSources
        val target = current.find { it.id == sourceId } ?: return this
        val remaining = current.filterNot { it.id == sourceId }
        val reordered = remaining + target
        return normalizeFromOrder(reordered)
    }

    /**
     * Moves the specified source to the very bottom (lowest zIndex / background).
     */
    fun sendToBack(sourceId: String): Scene {
        val current = sortedSources
        val target = current.find { it.id == sourceId } ?: return this
        val remaining = current.filterNot { it.id == sourceId }
        val reordered = listOf(target) + remaining
        return normalizeFromOrder(reordered)
    }

    /**
     * Moves the specified source one step up in the layer hierarchy.
     */
    fun moveUp(sourceId: String): Scene {
        val current = sortedSources.toMutableList()
        val index = current.indexOfFirst { it.id == sourceId }
        if (index == -1 || index == current.lastIndex) return this

        // Swap with layer above
        val temp = current[index]
        current[index] = current[index + 1]
        current[index + 1] = temp

        return normalizeFromOrder(current)
    }

    /**
     * Moves the specified source one step down in the layer hierarchy.
     */
    fun moveDown(sourceId: String): Scene {
        val current = sortedSources.toMutableList()
        val index = current.indexOfFirst { it.id == sourceId }
        if (index <= 0) return this

        // Swap with layer below
        val temp = current[index]
        current[index] = current[index - 1]
        current[index - 1] = temp

        return normalizeFromOrder(current)
    }

    /**
     * Duplicates this scene, generating new unique IDs for both the scene and all of its sources,
     * ensuring existing scene/source relationships remain completely uncorrupted.
     */
    fun duplicate(
        newSceneId: String = UUID.randomUUID().toString(),
        newSceneName: String = "$name (Copy)",
        idGenerator: () -> String = { UUID.randomUUID().toString() }
    ): Scene {
        val duplicatedSources = sources.map { src ->
            src.duplicate(newId = idGenerator(), newName = src.name)
        }
        return copy(
            id = newSceneId,
            name = newSceneName,
            sources = duplicatedSources,
            orderIndex = orderIndex + 1
        ).normalizeLayers()
    }
}
